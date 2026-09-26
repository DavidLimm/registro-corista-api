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
Apoio / Maestro / Pastor:      AppUser → Pessoa
Corista (adolescente/jovem):   AppUser → Pessoa → Corista
```

- `Pessoa` = dados comuns a todos (nome, data de nascimento, endereço, telefone, congregação, status).
- `Corista` = especialização **1:1** de `Pessoa` (FK `pessoa_id UNIQUE`), com apenas os campos exclusivos de corista (tipo de voz, tamanho de camisa, ocupação, etc.). Isso evita tabela cheia de campos nulos **e** duplicação de identidade.
- Nem toda `Pessoa` tem `AppUser` (ex.: menor gerenciado pelo líder). Todo `AppUser` aponta para uma `Pessoa`.
- NÃO existe entidade `Membro` único. NÃO recriar corista como tabela solta que duplica dados de pessoa.

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
- Entidades: `Area`, `Congregacao`, `Pessoa`, `Corista`, `Endereco`, `Telefone`, `Evento`, `AppUser`, `Role`
- Tabela de usuários nomeada `app_user` (não `user`) para evitar conflito com palavra reservada no PostgreSQL
- IDs em `UUID` (`gen_random_uuid()`), padrão adotado nas migrations

## Regras de Negócio Críticas — Não Alterar Sem Confirmação

- Modelo de dados e relacionamentos entre entidades (especialização `Pessoa` 1:1 `Corista`)
- **Classificação de faixa etária:**
    - Faixa etária real: derivada da data de nascimento, nunca gravada. Adolescente até 17a 11m 30d; jovem a partir de 18a.
    - `lista_classificacao` (ADOLESCENTE | JOVEM): campo gravado em `Corista`, por padrão segue a idade.
    - **Promoção antecipada:** só o `LIDER_MOCIDADE` pode promover um adolescente para a lista de jovens. Apenas nesse sentido, **permanente**, com trilha (`promovido_por`, `promovido_em`). Menor promovido continua menor → proteções LGPD seguem valendo.
- **Workflow de cadastro:** self-service entra como `PENDENTE` → aprovação por `PRESBITERO` (ou papel superior) → `APROVADO`. Remoção = soft delete (`INATIVO`), nunca DELETE físico.
- **Regras de escopo do RBAC** (nomes conforme `V7__role_table.sql` — ver também a seção "Escopo de acesso por Área" abaixo):
    - `ADMIN`: global
    - `PASTOR`: área (leitura geral)
    - `PRESBITERO`: congregação
    - `LIDER_MOCIDADE` / `LIDER_JOVENS` / `LIDER_ADOLESCENTES`: área, com restrições ao recorte da mocidade
    - `MAESTRO_ADOLESCENTES` / `MAESTRO_JOVENS`: restrito ao seu recorte
    - `APOIO_ADOLESCENTES` / `APOIO_JOVENS`: acesso operacional limitado ao seu recorte
    - `CORISTA_ADOLESCENTES` / `CORISTA_JOVENS`: apenas o próprio perfil + ver eventos do seu escopo
- **LGPD:** cadastro de menores exige responsável legal (nome + telefone) + consentimento explícito + trilha de aprovação/alteração.

### Escopo de acesso por Área (RBAC) — decidido em 2026-08-30, pendente de implementação

**Ainda não existe nenhuma infraestrutura de autenticação/autorização no projeto** (sem Spring Security configurado, sem JWT, sem login, sem forma de saber "quem é o usuário atual" em uma requisição). O que segue são as regras que devem ser aplicadas **quando** essa parte for construída — não implementar nada disto agora, é só o registro da decisão para não perder o rumo.

**Roles** (`V7__role_table.sql`, tabela `role`): `ADMIN`, `PASTOR`, `PRESBITERO`, `LIDER_MOCIDADE`, `LIDER_JOVENS`, `LIDER_ADOLESCENTES`, `APOIO_ADOLESCENTES`, `APOIO_JOVENS`, `MAESTRO_ADOLESCENTES`, `MAESTRO_JOVENS`, `CORISTA_ADOLESCENTES`, `CORISTA_JOVENS`.

> Nomenclatura padronizada em 2026-09-25: a seção "Regras de escopo do RBAC" (acima) usava nomes antigos (`RESP_MOCIDADE`, `RESP_JOVEM`, `RESP_ADOLESCENTE`, `MAESTRO`, `APOIO`, `CORISTA`) — já atualizada para usar exatamente estes nomes seedados no banco. Mesma padronização aplicada em `ListaClassificacao` (Javadoc), `StatusPessoa` (Javadoc) e `README.md`.

**Regra de escopo por Área (nova, definida nesta sessão):**
- `ADMIN`: acesso global a todas as áreas — bypass total, não precisa de vínculo de área.
- `PASTOR`: pode ser designado a **até 2 áreas**.
- Todas as demais roles (`LIDER_MOCIDADE` para baixo — líderes, apoio, maestros, coristas): designadas a **exatamente 1 área**.
- Motivação: dashboards futuros (ex.: "quantos jovens ao todo", "quantos por área", "quantos por congregação") dependem desse vínculo para filtrar o que cada usuário pode ver — `ADMIN` vê tudo, os demais só o recorte da(s) sua(s) área(s).

**Modelo de dados recomendado (ainda não criado):** uma tabela de junção `user_area` (many-to-many entre `AppUser` e `Area`), e não uma coluna `area_id` direto em `AppUser` — porque `PASTOR` pode ter 2 áreas, o que uma coluna única não suporta. A validação de quantidade máxima (1 para a maioria, 2 para `PASTOR`, nenhuma exigida para `ADMIN`) é regra de negócio da camada `Service`, não constraint de banco.

**Quando a autenticação for implementada:**
1. Login + emissão de JWT usando `AppUser.email`/`passwordHash` (já existem no schema).
2. `SecurityConfig` + filtro que extrai o usuário autenticado da requisição.
3. Toda consulta que lista/filtra por área (ou por congregação, para roles com esse recorte — ver seção acima) deve aplicar o vínculo de `user_area` do usuário autenticado, exceto `ADMIN`.

Qualquer mudança nesses pontos exige confirmação explícita do responsável pelo projeto antes da implementação.

## Migrations

- Toda alteração de schema deve ser feita via migration **Flyway**, nunca via `ddl-auto: update`. Em dev/prod usar `ddl-auto: validate`
- ⚠️ **Spring Boot 4:** o Flyway NÃO é mais auto-configurado só com `flyway-core`. É obrigatório usar `spring-boot-starter-flyway` + `flyway-database-postgresql`, senão as migrations não rodam (silenciosamente). Mesma lógica para módulos de teste: `spring-boot-starter-<tech>-test`.
- Migrations são versionadas e nunca devem ser editadas após aplicadas — criar uma nova migration para correções
- Ordem atual: V1 endereco · V2 area · V3 congregacao · V4 pessoa · V5 telefone · V6 corista · V7 role · (próximas: app_user/role, event)

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