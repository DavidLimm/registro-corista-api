# AGENTS.md

Instruções para agentes de IA (ex: Claude Code) trabalhando neste repositório.

## Visão Geral do Projeto

Sistema de gestão da mocidade (coristas) da **Assembleia de Deus em Pernambuco**, com hierarquia
**Área → Congregação → Pessoa → (Corista)** e controle de acesso baseado em papéis (RBAC).

Hierarquia real: a IEAD-PE(Igreja Evangelica Assembleia de Deus Pernambuco) tem 74 áreas (matriz = Área 1).
Cada área tem N congregações. MVP cobre apenas a **Área 40** (13 congregações), mas o modelo já nasce multi-área.

Stack: Java 21 + Spring Boot 4.1 + PostgreSQL 16 (Docker) + Spring Data JPA/Hibernate + Flyway + Spring Security (JWT).

## Arquitetura

- **MVC clássico em camadas** (`Controller → Service → Repository → DB`)
- Pacotes organizados **por feature/domínio** (`area`, `congregacao`, `pessoa`, `corista`, `evento`, `usuario`/security...),
  e **dentro** de cada um a divisão controller/service/repository/model/dto. NÃO organizar por camada técnica global.
- DTOs obrigatórios na camada de apresentação — entidades JPA nunca expostas diretamente na API
- Validações de negócio exclusivamente na camada `Service`, nunca na `Controller`
- RBAC com escopo contextual: toda operação valida **role** e **vínculo geográfico** (área/congregação) do recurso acessado, sempre no backend — nunca confiar no frontend
- Decisão registrada: **não** migrar para monolito modular agora (over-engineering para projeto solo). MVC por feature já pavimenta essa evolução se necessário.

## Modelo de Identidade (decisão central — não reverter)

Uma pessoa é representada **uma única vez**. Papéis são vínculos, não tabelas duplicadas.

```
Apoio / Maestro / Pastor:      Usuario → Pessoa
Corista (adolescente/jovem):   Usuario → Pessoa → Corista
```

- `Pessoa` = dados comuns a todos (nome, data de nascimento, endereço, telefone, congregação, status).
- `Corista` = especialização **1:1** de `Pessoa` (FK `pessoa_id UNIQUE`), com apenas os campos exclusivos de corista (tipo de voz, tamanho de camisa, ocupação, etc.). Isso evita tabela cheia de campos nulos **e** duplicação de identidade.
- Nem toda `Pessoa` tem `Usuario` (ex.: menor gerenciado pelo líder). Todo `Usuario` aponta para uma `Pessoa`.
- NÃO existe entidade `Membro` único. NÃO recriar corista como tabela solta que duplica dados de pessoa.

## Hierarquia Organizacional (decidido em 2026-10-10 — pendente de implementação)

Substitui a visão simplificada "Área → Congregação" do início deste arquivo (e o modelo atual da tabela `area`).
Só entra no código via nova migration Flyway; nunca editar V1–V15. Mudar este modelo exige confirmação explícita.

```
Agrupamento (tipo)  →  Subdivisão (nível do meio)  →  Congregação
```

| Tipo do agrupamento | Identificação | Nível do meio | Exemplo |
|---|---|---|---|
| `CAMPO_MISSIONARIO` | nome + **país obrigatório** | **Local**, com nome | campo (Argentina) → Local Alejandro Korn → congregação |
| `FILIAL` | nome | **Área**, com número | Petrolina → Área 1 → Alto do Vento |
| `SETOR` | número + nome | **Área**, com número | Setor 1 (Cabo de Santo Agostinho) → Área 10 → Alto Bela Vista |
| `AREA` | número | nenhum (subdivisão implícita) | Área 40 → Acampamento 2 |

- Tabelas: `agrupamento` (`id`, `tipo`, `nome`, `numero`, `pais`, `ativo`), `subdivisao` (`id`, `agrupamento_id`, `numero`, `nome`, `ativo`),
  `congregacao.subdivisao_id` NOT NULL. O **tipo** é enum fixo no código; as **instâncias** são linhas criadas pelo sistema
  (o front lista o enum, mas quem cria agrupamentos/subdivisões são os usuários autorizados).
- Para o tipo `AREA`, ao criar o agrupamento o sistema cria automaticamente uma única subdivisão implícita (sem número/nome),
  para a congregação sempre apontar para `subdivisao`.
- Números se repetem entre agrupamentos (Área 3 do setor ≠ Área 3 da filial): `numero` só é único **dentro do agrupamento**
  (`UNIQUE (agrupamento_id, numero)`). Número de setor único entre setores; número de agrupamento `AREA` único entre os `AREA`.
- Um agrupamento nunca enxerga/acessa dados de outro: o isolamento vem do vínculo de escopo do usuário.
- Validação de campos obrigatórios por tipo é regra de negócio da camada `Service` (complementada por constraints).

**Escopo do usuário (revisa a seção "Escopo de acesso por Área" abaixo):** o vínculo é com o **agrupamento**
(tabela de junção `usuario_agrupamento`, não `usuario_area`). `ADMIN`: global. `PASTOR`: até **2 agrupamentos**, de quaisquer tipos
(ex.: filial + setor, setor + área) — **exceção:** se um dos vínculos é `CAMPO_MISSIONARIO`, o pastor fica **apenas** nele.
Demais roles: exatamente 1 agrupamento. Quantidade máxima é regra do `Service`, não constraint de banco.

## Comandos

> ⚠️ A preencher conforme o projeto for inicializado

```bash
# Subir banco (Postgres via Docker)
docker compose up -d

# Build
./mvnw clean install

# Rodar localmente
./mvnw spring-boot:run

# Rodar testes
./mvnw test
```

## Estratégia de Testes

- Testes unitários obrigatórios na camada `Service` (JUnit 5 + Mockito)
- Testes de integração de endpoints com Spring Boot Test
- Banco de teste: **Testcontainers com PostgreSQL** — evitar H2, pois o projeto depende de comportamentos específicos do Postgres
- TDD não é obrigatório, mas **nenhuma funcionalidade nova deve ser integrada sem teste mínimo do Service correspondente**

## Padrões de Código

- Todo o código (classes, métodos, variáveis, tabelas e colunas) em **Português**.
- Lombok para reduzir boilerplate (getters/setters, builders)
- DTOs como Records quando possível
- Uso de `Optional<T>` para retornos que podem ser nulos — evitar retorno de `null` direto
- Entidades: `Area`, `Congregacao`, `Pessoa`, `Corista`, `Endereco`, `Telefone`, `Evento`, `Usuario`, `Role`
- Tabela de usuários nomeada `usuario` (não `user`) para evitar conflito com palavra reservada no PostgreSQL
- IDs em `UUID` (`gen_random_uuid()`), padrão adotado nas migrations

## Regras de Negócio Críticas — Não Alterar Sem Confirmação

- Modelo de dados e relacionamentos entre entidades (especialização `Pessoa` 1:1 `Corista`)
- **Classificação de faixa etária:**
    - Faixa etária real: derivada da data de nascimento, nunca gravada. Adolescente até 17a 11m 30d; jovem a partir de 18a.
    - `lista_classificacao` (ADOLESCENTE | JOVEM): campo gravado em `Corista`, por padrão segue a idade.
    - **Promoção antecipada:** só o `LIDER_MOCIDADE` pode promover um adolescente para a lista de jovens. Apenas nesse sentido, **permanente**, com trilha (`promovido_por`, `promovido_em`). Menor promovido continua menor → proteções LGPD seguem valendo. A aprovação/reprovação de cadastro (abaixo) **nunca** muda `lista_classificacao` — são mecanismos independentes.
- **Workflow de cadastro (revisado em 2026-09-27):** todo mundo pode se auto-cadastrar e já sair com login (`Usuario`) — `Pessoa`/`Corista` e `Usuario` nascem juntos na mesma requisição quando o cliente informa e-mail/senha (ver `CoristaRequest.usuario`). Tudo nasce `PENDENTE`. O papel do `Usuario` nesse auto-cadastro é sempre derivado (`CORISTA_ADOLESCENTES`/`CORISTA_JOVENS` pela lista de classificação) — nunca escolhido pelo cliente, para não permitir auto-atribuição de papel. A partir de `PENDENTE`, um líder aprova (`PENDENTE -> APROVADO`) ou reprova (`PENDENTE -> REPROVADO`, status próprio, distinto de `INATIVO`). O "acesso" de fato (login funcionar) só vale após `APROVADO` (ver a seção "Segurança"). Remoção continua sendo soft delete (`INATIVO`), nunca DELETE físico.
- **Afastamento (decidido em 2026-10-03):** cadastro `APROVADO` pode virar `AFASTADO` e voltar a `APROVADO`. Só `LIDER_MOCIDADE` e `PASTOR` (permissão `CORISTA_APROVAR_AFASTAMENTO`) fazem essas transições; os demais papéis apenas solicitam (`CORISTA_SOLICITAR_AFASTAMENTO`). Quem está `AFASTADO` **não tem acesso ao sistema**: o login é bloqueado (e os tokens já emitidos param de valer na hora). Toda transição de status fica registrada em `historico_status_pessoa`.
- **Quem aprova/reprova (revisado em 2026-09-27 — tira essa exclusividade do `PRESBITERO`):**
    - Adolescentes: `DIRIGENTE_UNIAO` (dirigente da união, verifica se a pessoa é mesmo da congregação).
    - Jovens: `LIDERANCA_GRUPO_JOVEM` e `DIRIGENTE_CAMPANHA`.
    - Na prática, qualquer papel de liderança pode aprovar/reprovar (`LIDER_MOCIDADE`/`LIDER_JOVENS`/`LIDER_ADOLESCENTES` inclusos) — a validação de **qual líder pode aprovar o quê** é feita pelo recorte de acesso (`RecorteDeAcesso`: papel → lista adolescentes/jovens) nos endpoints de `/coristas`; quem aprovou vem do usuário logado. Em `/pessoas/{id}/aprovar` basta a permissão `PESSOA_APROVAR` (só `LIDER_MOCIDADE`, `PASTOR` e `ADMIN`).
    - `PRESBITERO` deixou de aprovar/reprovar por padrão: hoje é só acesso a dashboards (mesma lógica de "pode ou não aprovar" fica a critério de cada liderança, não dele).
- **Regras de escopo do RBAC** (nomes conforme `V7__role_table.sql`/`V13__novos_roles_aprovacao.sql` — ver também a seção "Escopo de acesso por Área" abaixo):
    - `ADMIN`: global
    - `PASTOR`: área (leitura geral)
    - `PRESBITERO`: congregação (dashboards; não aprova/reprova por padrão — ver acima)
    - `LIDER_MOCIDADE` / `LIDER_JOVENS` / `LIDER_ADOLESCENTES`: área, com restrições ao recorte da mocidade
    - `DIRIGENTE_UNIAO`: área; aprova/reprova cadastro de adolescentes
    - `LIDERANCA_GRUPO_JOVEM` / `DIRIGENTE_CAMPANHA`: área; aprovam/reprovam cadastro de jovens
    - `MAESTRO_ADOLESCENTES` / `MAESTRO_JOVENS`: restrito ao seu recorte
    - `APOIO_ADOLESCENTES` / `APOIO_JOVENS`: acesso operacional limitado ao seu recorte
    - `CORISTA_ADOLESCENTES` / `CORISTA_JOVENS`: apenas o próprio perfil + ver eventos do seu escopo
- **LGPD:** cadastro de menores exige responsável legal (nome + telefone) + consentimento explícito + trilha de aprovação/alteração.

### Escopo de acesso por Área (RBAC) — decidido em 2026-08-30, **escopo geográfico ainda pendente**

A autenticação/autorização por permissão **já existe** (ver "Segurança"). O que segue — o vínculo do usuário com área — **ainda não foi implementado**, e foi revisado pela seção "Hierarquia Organizacional" acima (o vínculo passa a ser com o **agrupamento**, tabela `usuario_agrupamento`).

**Roles** (`V7__role_table.sql` + `V13__novos_roles_aprovacao.sql`, tabela `role`): `ADMIN`, `PASTOR`, `PRESBITERO`, `LIDER_MOCIDADE`, `LIDER_JOVENS`, `LIDER_ADOLESCENTES`, `DIRIGENTE_UNIAO`, `LIDERANCA_GRUPO_JOVEM`, `DIRIGENTE_CAMPANHA`, `APOIO_ADOLESCENTES`, `APOIO_JOVENS`, `MAESTRO_ADOLESCENTES`, `MAESTRO_JOVENS`, `CORISTA_ADOLESCENTES`, `CORISTA_JOVENS`.

> Nomenclatura padronizada em 2026-09-25: a seção "Regras de escopo do RBAC" (acima) usava nomes antigos (`RESP_MOCIDADE`, `RESP_JOVEM`, `RESP_ADOLESCENTE`, `MAESTRO`, `APOIO`, `CORISTA`) — já atualizada para usar exatamente estes nomes seedados no banco. Mesma padronização aplicada em `ListaClassificacao` (Javadoc), `StatusPessoa` (Javadoc) e `README.md`.

**Regra de escopo por Área (nova, definida nesta sessão):**
- `ADMIN`: acesso global a todas as áreas — bypass total, não precisa de vínculo de área.
- `PASTOR`: pode ser designado a **até 2 áreas**.
- Todas as demais roles (`LIDER_MOCIDADE` para baixo — líderes, apoio, maestros, coristas): designadas a **exatamente 1 área**.
- Motivação: dashboards futuros (ex.: "quantos jovens ao todo", "quantos por área", "quantos por congregação") dependem desse vínculo para filtrar o que cada usuário pode ver — `ADMIN` vê tudo, os demais só o recorte da(s) sua(s) área(s).

**Modelo de dados recomendado (ainda não criado):** uma tabela de junção `usuario_area` (many-to-many entre `Usuario` e `Area`), e não uma coluna `area_id` direto em `Usuario` — porque `PASTOR` pode ter 2 áreas, o que uma coluna única não suporta. A validação de quantidade máxima (1 para a maioria, 2 para `PASTOR`, nenhuma exigida para `ADMIN`) é regra de negócio da camada `Service`, não constraint de banco.

**Quando o escopo geográfico for implementado:**
1. (feito) Login + JWT e filtro que extrai o usuário autenticado.
2. Toda consulta que lista/filtra por área (ou por congregação, para roles com esse recorte — ver seção acima) deve aplicar o vínculo de `user_area` do usuário autenticado, exceto `ADMIN`.

Qualquer mudança nesses pontos exige confirmação explícita do responsável pelo projeto antes da implementação.

## Migrations

- Toda alteração de schema deve ser feita via migration **Flyway**, nunca via `ddl-auto: update`. Em dev/prod usar `ddl-auto: validate`
- ⚠️ **Spring Boot 4:** o Flyway NÃO é mais auto-configurado só com `flyway-core`. É obrigatório usar `spring-boot-starter-flyway` + `flyway-database-postgresql`, senão as migrations não rodam (silenciosamente). Mesma lógica para módulos de teste: `spring-boot-starter-<tech>-test`.
- Migrations são versionadas e nunca devem ser editadas após aplicadas — criar uma nova migration para correções
- Ordem atual: V1 endereco · V2 area · V3 congregacao · V4 pessoa · V5 telefone · V6 corista · V7 role ·
  V8/V9 ajustes de corista · V10 soft delete/trilha em role · V11 app_user/app_user_role (renomeadas na V15) ·
  V12 reverte FKs prematuras de V11 (pessoa.aprovado_por/corista.promovido_por) · V13 novos roles de aprovação +
  descrição do PRESBITERO · V14 status REPROVADO em pessoa · V15 renomeia app_user → usuario e app_user_role →
  usuario_role · V16 status AFASTADO · V17 permissao/role_permissao · V18 permissão de aprovar afastamento ·
  V19 trilha de afastamento · V20 historico_status_pessoa · V21 corista só vê o próprio cadastro · V22 permissão de
  corrigir nascimento · V23 bloqueio de login + redefinição de senha · V24 edição de contato · V25 desligamento de
  corista · V26 solicitação de papel · V27 solicitação de afastamento (próxima: V28 — hierarquia organizacional)

## Segurança (implementada em 2026-10-10)

Autenticação **stateless por JWT** + autorização por **permissão lida do banco**. Tudo no backend, nunca no front.

- **Login** `POST /v1/api/auth/login` (e-mail normalizado: trim + minúsculas). Resposta `{token, email, roles}`. O token leva só
  o e-mail e o `iat`; o filtro (`JwtAutenticacaoFilter`) recarrega o `Usuario` do banco a cada requisição, então afastar,
  reprovar, inativar, bloquear ou trocar papel vale **na hora**. Segredo em `jwt.segredo` (Base64, ≥ 32 bytes; env
  `JWT_SEGREDO`, **sem default em prod**); validade `jwt.expiracao-minutos` (default 60).
- **Quem entra** (`Usuario.isEnabled()`): `ativo` **e** pessoa `APROVADO`. O motivo do bloqueio só aparece **depois** de a senha
  conferir (403 com a causa: pendente, reprovado, afastado, inativo); senha errada e e-mail inexistente dão o mesmo 401.
- **Senha:** mínimo 8 caracteres com maiúscula, minúscula, número e caractere especial (`@SenhaForte`). **3 senhas erradas
  seguidas bloqueiam a conta** (423) até a senha ser redefinida. Redefinição por token aleatório (só o hash SHA-256 fica no
  banco, vale 30 min, uso único) em `/auth/redefinicao-senha/solicitar|confirmar`; `PUT /auth/senha` troca a senha de quem está
  logado. Trocar/redefinir senha invalida os tokens emitidos antes (`senha_alterada_em` × `iat`).
- ⚠️ **E-mail:** o envio é a porta `EnviadorDeEmail`. Em dev o token vai para o log; **em prod não há provedor configurado**
  (`EnviadorDeEmailNaoConfigurado`), então a redefinição por conta própria não funciona até implementar o envio real.
- **Autorização:** `@PreAuthorize("hasAuthority('<PERMISSAO>')")` nos controllers (as permissões vêm de `role_permissao`, editáveis
  pelo PASTOR em `PUT /roles/{id}/permissoes`; o `ADMIN` não é editável pela API). Papel é só um agrupador de permissões.
  A matriz endpoint × permissão é conferida por `AutorizacaoEndpointsIntegracaoTest`: **todo endpoint novo entra nessa matriz**.
- **Público (sem login):** `POST /auth/login`, `POST /auth/redefinicao-senha/**`, `POST /coristas` (auto-cadastro), `GET /congregacoes`
  e `/congregacoes/{id}` (o formulário lista as congregações), Swagger (desligado em prod) e `/error`. O resto exige token.
- **Recorte contextual** (no `Service`, não no controller): `RecorteDeAcesso` mapeia papel → listas (adolescentes e/ou jovens) que
  ele enxerga; papel fora do mapa só acessa o próprio cadastro. Fora do recorte a resposta é **404** (não revela que existe).
  Presbítero vê as duas listas, só da própria congregação. Corista só vê/edita o **próprio** cadastro (e só se tiver papel
  `CORISTA_*`). `aprovadoPor`/`reprovadoPor`/`afastadoPor`/`retornadoPor`/`promovidoPor` vêm do usuário logado, nunca do cliente.
- **Regras decididas:** a data de nascimento só o líder corrige (`CORISTA_CORRIGIR_NASCIMENTO`; o corista não altera a própria);
  o corista edita o próprio telefone/endereço no máximo **1 vez a cada 10 dias** (telefone e endereço contam separado; líderes
  não são limitados); **nenhum corista é líder**: papel `CORISTA_*` não convive com outro papel e, ao receber um papel de liderança,
  o cadastro de corista é **desligado** (`desligado_em`, `motivo_desligamento = VIROU_LIDER`; não apaga, some das listas e do acesso);
  quem sai ou passa da idade continua sendo `INATIVO`.
- **Fluxos:** afastamento — quem só pode solicitar usa `POST /coristas/{id}/solicitar-afastamento`, e `CORISTA_APROVAR_AFASTAMENTO`
  decide em `/solicitacoes-afastamento` (aprovar afasta a pessoa); papéis — `ROLE_SOLICITAR` pede (`POST /roles/solicitacoes`, nasce
  `PENDENTE` e inativo) e `ROLE_APROVAR` decide.
- **Testes de segurança:** `@WithUsuarioTeste` (pacote `suporte`) autentica como um `Usuario` real; os testes de controller importam
  `SegurancaTesteConfig`.
- **Pendências conhecidas:** escopo por agrupamento/área (vem com a hierarquia nova); limite de tentativas no `POST /coristas` público;
  CORS; botão de Bearer no Swagger.

## Observabilidade

- Spring Boot Actuator (health/readiness/liveness)
- Micrometer (métricas → Prometheus)
- Micrometer Tracing + OpenTelemetry (nativo no Boot 4)
- Logs estruturados em JSON
- Tratamento de erro global via `@ControllerAdvice` (Problem Details / RFC 7807)
- Documentação de API: springdoc-openapi (Swagger UI)

## Perfis (Spring Profiles)

- `application.yaml`: perfil padrão (dev) — usado quando nenhum profile é ativado. Tem defaults seguros pra rodar local (`DB_USER`/`DB_PASSWORD` com fallback `postgres`, `show-sql: true`, Swagger habilitado).
- `application-prod.yaml`: ativado via `SPRING_PROFILES_ACTIVE=prod` na plataforma de deploy (Render). Sem defaults nas credenciais do banco (falha ao subir se a env var não existir, em vez de cair silenciosamente num valor fraco), `ddl-auto` fixo em `validate` (não pode ser sobrescrito por env var), `show-sql`/`format_sql` desligados, Swagger/OpenAPI desabilitado (não expor documentação da API publicamente).

## Variáveis de Ambiente e Segredos

- Nunca commitar segredos. Usar `.env` local (ignorado no Git) pro perfil dev; em prod, todas as variáveis (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `SPRING_PROFILES_ACTIVE=prod`) vêm da plataforma (Render). Banco em prod: Render Postgres ou Neon.

## Convenção de Commits e Branches

- **GitHub Flow:** `main` sempre estável e deployável. Nada de push direto — tudo via Pull Request.
- **Branches:** `feat/<area>-<atividade>`, `fix/<...>`, `chore/<...>`. Uma atividade por branch.
- **Commits:** Conventional Commits (`feat:`, `fix:`, `chore:`, `test:`, `docs:`, `refactor:`).
- **Definition of Done:** compila + testes verdes + PR revisado + doc atualizada quando aplicável.