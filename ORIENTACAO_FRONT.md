# ORIENTAÇÃO PARA O FRONT-END

Guia completo para quem constrói o front que consome a **registro-coristas-api**: cada rota, cada regra, cada tela que a API
espera, a segurança, o endereço (CEP) e o que ainda vem pela frente (Auth0). Este arquivo é para ser copiado para o repositório do
front. A fonte da verdade continua sendo o back: em caso de dúvida, o Swagger (`/swagger-ui.html`, só em dev) e o `AGENTS.md`/`README.md`
da API.

> Estado deste documento: reflete a API na branch `feat/hierarquia-agrupamento` (migrations até a **V32**), com o Auth0 ainda
> **planejado** (seção 13).

## Sumário

1. [Visão geral do sistema](#1-visão-geral-do-sistema)
2. [Convenções da API](#2-convenções-da-api)
3. [Autenticação e segurança](#3-autenticação-e-segurança)
4. [Papéis, permissões e o que cada tela exige](#4-papéis-permissões-e-o-que-cada-tela-exige)
5. [Hierarquia e escopo (o que cada usuário enxerga)](#5-hierarquia-e-escopo-o-que-cada-usuário-enxerga)
6. [Telas e fluxos](#6-telas-e-fluxos)
7. [Referência das rotas](#7-referência-das-rotas)
8. [Tipos TypeScript](#8-tipos-typescript)
9. [Enumerações](#9-enumerações)
10. [Validações para repetir no front](#10-validações-para-repetir-no-front)
11. [Endereço e CEP](#11-endereço-e-cep)
12. [Pendências do back que afetam o front](#12-pendências-do-back-que-afetam-o-front)
13. [Auth0 (próximo passo do back)](#13-auth0-próximo-passo-do-back)
14. [Checklist de implementação](#14-checklist-de-implementação)

---

## 1. Visão geral do sistema

Gestão da mocidade (coristas) da **Assembleia de Deus em Pernambuco (IEAD-PE)**.

```
Agrupamento (tipo)  →  Subdivisão  →  Congregação  →  Pessoa  →  (Corista)
```

| Conceito | O que é |
|---|---|
| **Agrupamento** | Nível mais alto. Tem um **tipo**: `CAMPO_MISSIONARIO` (no exterior), `FILIAL`, `SETOR` ou `AREA`. As **74 áreas já existem** no banco. |
| **Subdivisão** | Nível do meio. Em campo missionário é um **Local** (tem nome); em filial e setor é uma **Área** (tem número); no tipo `AREA` é **implícita** (sem número nem nome). |
| **Congregação** | A igreja local. Pertence a uma subdivisão. |
| **Pessoa** | Dados comuns (nome, nascimento, congregação, endereço, status). Uma pessoa existe **uma vez só**. |
| **Corista** | Especialização 1:1 de pessoa (voz, camisa, ocupação, lista adolescente/jovem). |
| **Usuário (login)** | Credenciais de uma pessoa (e-mail + senha). Nem toda pessoa tem login. |

Exemplos reais de caminho: `Argentina (campo) → Local Alejandro Korn → congregação`; `Petrolina (filial) → Área 1 → congregação`;
`Setor 1 – Cabo de Santo Agostinho → Área 10 → congregação`; `Área 40 → congregação` (a subdivisão é implícita).

**Duas classificações que não se confundem**
- **Faixa etária** (`faixaEtaria`): derivada da data de nascimento, nunca gravada. Adolescente até 17a 11m 30d; jovem a partir de 18.
- **Lista de classificação** (`listaClassificacao`, só no corista): é gravada. Nasce igual à idade; só o `LIDER_MOCIDADE` pode promover
  adolescente → jovem antes dos 18 (permanente, só nesse sentido). Aprovar/reprovar cadastro **nunca** muda a lista.

**Status da pessoa** (`status`): `PENDENTE` → `APROVADO` ou `REPROVADO`; `APROVADO` ⇄ `AFASTADO`; remoção é `INATIVO` (soft delete, nunca
apaga). **Só quem está `APROVADO` consegue entrar no sistema.**

---

## 2. Convenções da API

| Item | Regra |
|---|---|
| **Base URL** | `http://localhost:8080/v1/api` em dev. Produção: URL do Render (a definir). Todas as rotas abaixo são relativas a `/v1/api`. |
| **Formato** | JSON (`Content-Type: application/json`). Textos em UTF-8. |
| **IDs** | `UUID` (string). |
| **Datas** | `LocalDate` como `"2000-05-17"`. Instantes (`criadoEm` etc.) em ISO-8601 UTC: `"2026-10-11T12:00:00Z"`. |
| **Autenticação** | `Authorization: Bearer <token>` em tudo, exceto as rotas públicas (seção 3.2). |
| **Idioma** | Campos, enums e mensagens de erro em **português**. |
| **Corpo vazio** | `DELETE` e várias ações devolvem `204 No Content`. |
| **Criação** | `POST` que cria devolve `201 Created` com o recurso no corpo e a URL dele no header `Location`. |

### 2.1 Paginação

Listagens grandes (`/coristas`, `/pessoas`, `/usuarios`, `/solicitacoes-afastamento`) são paginadas:

- Parâmetros: `page` (começa em **0**, padrão 0) e `size` (1 a **100**, padrão 20). A **ordenação é fixa** (por nome/e-mail): o cliente não escolhe.
- Resposta (Spring `PagedModel`):

```json
{
  "content": [ { "...": "..." } ],
  "page": { "size": 20, "number": 0, "totalElements": 134, "totalPages": 7 }
}
```

Outras listas (`/agrupamentos`, `/congregacoes`, `/roles`, subdivisões) vêm como **array simples**, sem paginação.

**Dica para dashboards (a API ainda não tem rotas de contagem):** peça `size=1` com os filtros desejados e leia `page.totalElements`.
Ex.: `GET /coristas?status=PENDENTE&size=1` → quantos cadastros pendentes.

### 2.2 Erros (Problem Details, RFC 7807)

Todo erro tem `Content-Type: application/problem+json` e este formato:

```json
{ "type": "about:blank", "title": "Conflict", "status": 409, "detail": "Já existe uma congregação chamada \"Sede\" nesta subdivisão", "instance": "/v1/api/congregacoes" }
```

Mostre ao usuário o campo **`detail`** (já em português e pensado para isso). Códigos usados:

| Status | Quando | O que o front faz |
|---|---|---|
| **400** | Corpo inválido (campo faltando, formato errado, enum inexistente), UUID mal formado, regra de negócio simples (ex.: menor sem responsável). | Mostrar o `detail`. **Atenção:** para erro de validação de campos, o `detail` é genérico (`"Invalid request content."`) e **não lista os campos**: por isso repita as validações da seção 10 no front. |
| **401** | Sem token, token inválido/expirado, ou e-mail/senha errados no login. | Ir para a tela de login (limpar o token). |
| **403** | Falta permissão; login de cadastro não liberado (com o motivo); ação fora dos seus agrupamentos (escrita de congregação). | Mostrar o `detail`. Não deslogar. |
| **404** | Não existe **ou está fora do seu escopo** (a API não revela que existe). | Tratar como "não encontrado". |
| **409** | Conflito de regra: duplicado, transição de status inválida, inativar algo com dependentes, escopo incompatível com o papel. | Mostrar o `detail`. |
| **423** | Conta bloqueada por 3 senhas erradas. | Mostrar o aviso e levar à redefinição de senha. |
| **429** | Pessoa editou telefone/endereço há menos de 10 dias. | Mostrar o `detail` (traz a data liberada). |

#### Erros de validação: padrão de mercado e o que a API faz hoje

O padrão de mercado é o **Problem Details (RFC 9457, antigo 7807)** com um membro extra **`errors`**: uma lista de `{ campo, mensagem }`,
um item por campo inválido, para o front marcar cada input. Exemplo do **formato-alvo** (ainda **não implementado** no back):

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Há campos inválidos.",
  "instance": "/v1/api/coristas",
  "errors": [
    { "campo": "pessoa.dataNascimento", "mensagem": "deve ser uma data passada" },
    { "campo": "usuario.senha", "mensagem": "deve ter ao menos 8 caracteres, com maiúscula, minúscula, número e caractere especial" }
  ]
}
```

**Hoje** a API devolve só `{"detail":"Invalid request content.","status":400,...}`, **sem** `errors`. Por isso:
1. Valide no front com as regras da seção 10 (continua necessário, para resposta imediata e sem ida ao servidor).
2. Escreva o tratador de erro **já preparado**: se vier `errors`, mostre cada mensagem no campo correspondente; senão, mostre o `detail`.
   Assim, quando o back passar a enviar `errors` (item da seção 12), nada muda no front.

```ts
function aplicarErros(p: Problema & { errors?: { campo: string; mensagem: string }[] }, marcarCampo: (campo: string, msg: string) => void) {
  if (p.errors?.length) p.errors.forEach(e => marcarCampo(e.campo, e.mensagem));
  else mostrarAviso(p.detail);
}
```

### 2.3 CORS e ambiente de desenvolvimento

> **Atenção:** o back **ainda não tem CORS configurado** (item da seção 12). Enquanto isso, em dev chame a API **pelo proxy do servidor
> de desenvolvimento** do front (Vite: `server.proxy = { '/v1': 'http://localhost:8080' }`; CRA/webpack: `proxy`). Assim o navegador
> enxerga a mesma origem e o CORS não atrapalha. O CORS de produção entra junto com o passo do Auth0.

### 2.4 Swagger / OpenAPI

Em dev: `http://localhost:8080/swagger-ui.html` e o contrato em `http://localhost:8080/v3/api-docs`. Dá para **gerar os tipos/cliente
do front** a partir desse JSON (ex.: `openapi-typescript`). Em produção está desligado.

---

## 3. Autenticação e segurança

> Hoje a API tem login próprio com JWT. O **Auth0** está planejado como próximo passo (seção 13): **isole toda a autenticação do front
> em um único módulo** (login, logout, obter token, anexar `Authorization`, tratar 401) para a troca ser barata.

### 3.1 Login

`POST /auth/login` (público)

```json
// request
{ "email": "ana@exemplo.com", "senha": "SenhaForte@123" }
// 200
{ "token": "eyJhbGciOi...", "email": "ana@exemplo.com", "roles": ["ROLE_LIDER_MOCIDADE", "CORISTA_LER", "CORISTA_APROVAR", "PESSOA_LER"] }
```

- O e-mail é normalizado (espaços e maiúsculas não importam).
- **`roles` mistura duas coisas:** os papéis (`ROLE_<NOME>`) e as **permissões** (`CORISTA_APROVAR`...). É daí que o front decide o que
  mostrar (seção 4). Guarde a lista junto com o token.
- O token vale **60 minutos** (configurável) e **não há refresh token**: ao expirar a API devolve 401 e o usuário entra de novo.
- O token só carrega e-mail e data de emissão: **não decodifique para checar permissão**; use `roles` do login. Quem manda é o back,
  que **recarrega o usuário a cada requisição**: afastar, reprovar, inativar, bloquear ou trocar papel vale na hora (a próxima chamada
  já devolve 401/403).
- Onde guardar o token: em memória (mais seguro; some no F5) ou `sessionStorage`. Evite `localStorage` se puder. Nunca em URL.

**Erros do login**

| Status | Significado | Mensagem sugerida |
|---|---|---|
| 401 | E-mail inexistente **ou** senha errada (a API não diz qual) | "E-mail ou senha inválidos." |
| 403 | Senha certa, mas o cadastro não libera acesso. O `detail` diz o motivo | mostre o `detail`: pendente de aprovação, reprovado, afastado, inativo, acesso desativado |
| 423 | Conta bloqueada (3 senhas erradas seguidas) | "Conta bloqueada. Redefina sua senha." |

> O motivo do 403 só aparece **depois** de a senha conferir (para não vazar quais e-mails existem).

### 3.2 Rotas públicas (sem token)

`POST /auth/login` · `POST /auth/redefinicao-senha/solicitar` · `POST /auth/redefinicao-senha/confirmar` · `POST /coristas` (auto-cadastro) ·
`GET /congregacoes` · `GET /congregacoes/{id}` · `GET /agrupamentos` · `GET /agrupamentos/{id}` · `GET /agrupamentos/{id}/subdivisoes` ·
`GET /agrupamentos/{id}/subdivisoes/{subId}`. **Todo o resto exige token.**

### 3.3 Senha

- Regra (`senha`, `novaSenha`): mínimo **8** caracteres, com **maiúscula, minúscula, número e caractere especial**; máximo 100.
- **3 senhas erradas seguidas** bloqueiam a conta (423) até a senha ser redefinida.
- `PUT /auth/senha` (logado) `{ "senhaAtual", "novaSenha" }` → `204`. **Trocar a senha derruba todos os tokens antigos**: depois do `204`,
  limpe o token e leve o usuário ao login.
- Esqueci a senha: `POST /auth/redefinicao-senha/solicitar` `{ "email" }` → **sempre `202`** (exista o e-mail ou não) → o usuário recebe
  um token por e-mail → `POST /auth/redefinicao-senha/confirmar` `{ "token", "novaSenha" }` → `204`. O token vale 30 minutos, é de uso único,
  e também **libera conta bloqueada**.
  - ⚠️ O envio real de e-mail **ainda não está configurado em produção**. Em dev o token aparece no **log do back**. Construa a tela, mas
    saiba que em produção ela só funcionará depois do provedor de e-mail (ou do Auth0, que assume isso).

### 3.4 Regras de segurança que o front deve respeitar

- **Nunca confie no front para segurança**: esconder um botão é conforto, não proteção. A API valida tudo (permissão, recorte e escopo).
- Trate **401 globalmente** (interceptor): limpar sessão e ir ao login.
- Trate **403** com o `detail`; não force logout.
- Quem autoriza ações (`aprovadoPor`, `afastadoPor`, `promovidoPor`...) é sempre o **usuário logado**: o front **não envia** o autor.
- O papel de um auto-cadastro é **sempre derivado** (corista adolescente/jovem): o front não escolhe papel no cadastro público.
- Não registre senha, token ou dados de menores em logs/analytics.

### 3.5 LGPD

Menores de 18 anos exigem **responsável legal** (nome + telefone) e **consentimento explícito**. O `nomeExibicao` de um menor já vem
como `"Pedro Campos (responsável: Maria Oliveira)"`: use esse campo nas listagens. Mostre o aviso de consentimento no cadastro.

---

## 4. Papéis, permissões e o que cada tela exige

### 4.1 Papéis (nomes exatos, como chegam em `roles` com o prefixo `ROLE_`)

| Papel | Quem é | Alcance |
|---|---|---|
| `ADMIN` | Administração do sistema | **Global**, todas as permissões |
| `PASTOR_ADMIN` | Pastor geral | **Global**, **somente leitura** nos dados; gerencia agrupamentos, subdivisões, o escopo e os logins de pastores |
| `PASTOR` | Pastor de área/setor/filial/campo | Seus agrupamentos (até 2; campo missionário fica sozinho) |
| `PRESBITERO` | Presbítero | Só a **sua congregação** (dashboards, leitura) |
| `LIDER_MOCIDADE` | Líder da mocidade | Seu agrupamento, as duas listas; promove, afasta, gere pessoas |
| `LIDER_ADOLESCENTES` / `LIDER_JOVENS` | Líderes de cada lista | Seu agrupamento, só a lista deles |
| `DIRIGENTE_UNIAO` | Dirigente da união | Aprova cadastro de **adolescentes** |
| `LIDERANCA_GRUPO_JOVEM` / `DIRIGENTE_CAMPANHA` | Liderança de jovens | Aprovam cadastro de **jovens** |
| `APOIO_ADOLESCENTES` / `APOIO_JOVENS` | Apoio | Operação limitada à lista deles |
| `MAESTRO_ADOLESCENTES` / `MAESTRO_JOVENS` | Maestro | Lê a lista dele e troca **só o tipo de voz** |
| `CORISTA_ADOLESCENTES` / `CORISTA_JOVENS` | Corista | **Só o próprio cadastro** |

Rótulos em português para a tela: ADMIN "Administrador", PASTOR_ADMIN "Pastor geral", PASTOR "Pastor", PRESBITERO "Presbítero",
LIDER_MOCIDADE "Líder da mocidade", LIDER_ADOLESCENTES "Líder dos adolescentes", LIDER_JOVENS "Líder dos jovens", DIRIGENTE_UNIAO
"Dirigente da união", LIDERANCA_GRUPO_JOVEM "Liderança do grupo de jovens", DIRIGENTE_CAMPANHA "Dirigente de campanha", APOIO_* "Apoio",
MAESTRO_* "Maestro", CORISTA_* "Corista".

Regras: `CORISTA_*` **não convive** com outro papel (quem vira líder deixa de ser corista: o cadastro de corista é desligado). Um usuário
tem 1 ou mais papéis; as permissões dele são a união.

### 4.2 Permissões (o que o front lê em `roles`) e o que cada uma libera

> As permissões de cada papel ficam no banco e **podem mudar sem deploy** (o `PASTOR` edita; `ADMIN` e `PASTOR_ADMIN` são fixos).
> Por isso **sempre decida pela permissão** recebida no login, nunca pelo nome do papel.

| Permissão | Libera |
|---|---|
| `CORISTA_CRIAR` | Cadastrar corista (logado) |
| `CORISTA_LER` | Listar/ver coristas (dentro do recorte e do escopo) |
| `CORISTA_ATUALIZAR` | Editar corista (o próprio, ou os do recorte se também tiver `CORISTA_LER`) |
| `CORISTA_ATUALIZAR_VOZ` | Trocar só o tipo de voz (maestro) |
| `CORISTA_INATIVAR` | Remover (soft) corista |
| `CORISTA_APROVAR` / `CORISTA_REPROVAR` | Aprovar / reprovar cadastro de corista |
| `CORISTA_PROMOVER` | Promover adolescente → jovem |
| `CORISTA_SOLICITAR_AFASTAMENTO` | Pedir o afastamento de um corista |
| `CORISTA_APROVAR_AFASTAMENTO` | Decidir pedidos de afastamento; afastar e retornar |
| `CORISTA_CORRIGIR_NASCIMENTO` | Alterar a data de nascimento de um corista |
| `PESSOA_CRIAR` / `LER` / `ATUALIZAR` / `INATIVAR` | CRUD de pessoa (telefones e endereços dependem de `LER`/`ATUALIZAR`) |
| `PESSOA_APROVAR` / `PESSOA_REPROVAR` | Aprovar / reprovar uma pessoa |
| `CONGREGACAO_GERENCIAR` | Criar, editar, mover, inativar congregações (só nos seus agrupamentos) |
| `AGRUPAMENTO_GERENCIAR` | Criar/editar/inativar agrupamentos e subdivisões (ADMIN e PASTOR_ADMIN) |
| `ESCOPO_GERENCIAR` | Definir os agrupamentos de cada usuário (ADMIN e PASTOR_ADMIN) |
| `USUARIO_GERENCIAR` | CRUD de logins |
| `ROLE_SOLICITAR` | Pedir um papel novo |
| `ROLE_APROVAR` | Aprovar/reprovar pedidos de papel |
| `ROLE_GERENCIAR_PERMISSOES` | Criar/editar papéis e editar as permissões de cada papel |
| `DASHBOARD_LER` | Acessar dashboards |

### 4.3 Quais permissões abrem cada item de menu

| Item de menu / tela | Mostrar se tiver |
|---|---|
| Meu perfil | qualquer usuário logado (`GET /coristas/meu` dá 404 para quem não é corista) |
| Lista de coristas | `CORISTA_LER` |
| Aprovações pendentes | `CORISTA_APROVAR` ou `CORISTA_REPROVAR` |
| Fila de afastamentos | `CORISTA_APROVAR_AFASTAMENTO` |
| Pessoas | `PESSOA_LER` |
| Congregações (gerenciar) | `CONGREGACAO_GERENCIAR` |
| Agrupamentos e subdivisões | `AGRUPAMENTO_GERENCIAR` |
| Usuários | `USUARIO_GERENCIAR` |
| Escopo dos usuários | `ESCOPO_GERENCIAR` |
| Papéis e permissões | `ROLE_GERENCIAR_PERMISSOES`; pedir papel: `ROLE_SOLICITAR`; decidir pedido: `ROLE_APROVAR` |
| Dashboard | `DASHBOARD_LER` (hoje só dá para montar com contagens por `totalElements`) |

---

## 5. Hierarquia e escopo (o que cada usuário enxerga)

Além da permissão, **cada usuário só enxerga e altera o que pertence aos agrupamentos a que está vinculado**:

- `ADMIN` e `PASTOR_ADMIN`: veem **tudo** (globais).
- `PASTOR`: até **2** agrupamentos (se um é campo missionário, fica só nele). **Sem vínculo, não enxerga nada.** O vínculo do pastor é
  definido por `ADMIN`/`PASTOR_ADMIN` (`PUT /usuarios/{id}/agrupamentos`).
- Demais papéis: **exatamente 1** agrupamento, herdado da congregação da pessoa quando o login é criado (inclusive no auto-cadastro).
- Fora do escopo: pessoa, corista, telefone, endereço, fila de afastamento e logins respondem **404**; escrever em congregação
  de outro agrupamento responde **403**.
- Os filtros `agrupamentoId`/`subdivisaoId` do cliente **só reduzem** o resultado, nunca ampliam o escopo.
- Sobre o recorte por lista: líder de adolescentes não vê jovens, e vice-versa; o presbítero só a própria congregação; o corista, só ele.

Para montar o **dashboard do pastor**: `GET /usuarios/meu/agrupamentos` devolve os agrupamentos dele (`global` é `true` para
ADMIN/PASTOR_ADMIN). Depois filtre as listagens por `agrupamentoId`.

---

## 6. Telas e fluxos

### 6.1 Auto-cadastro público (formulário de entrada)

1. **Escolha da congregação em cascata** (tudo público, sem token):
   1. Lista os **tipos** (enum fixo, seção 9): Campo Missionário, Filial, Setor, Área.
   2. `GET /agrupamentos?tipo=AREA&ativo=true` → para o tipo `AREA` aparecem as **74 áreas** (rótulo "Área {numero}").
   3. `GET /agrupamentos/{agrupamentoId}/subdivisoes?ativo=true` → **se o tipo for `AREA`, pule este passo**: vem uma única subdivisão sem
      número nem nome; use o `id` dela direto. Nos demais tipos o usuário escolhe o Local (campo) ou a Área (filial/setor).
   4. `GET /congregacoes?subdivisaoId={id}&ativa=true` → escolhe a congregação.
2. **Dados pessoais**: nome, data de nascimento, tipo de voz, tamanho de camisa, ocupação (opcional).
3. **Se menor de 18** (calcule pela data): exigir nome e telefone do responsável legal e o consentimento LGPD (checkbox).
4. **Endereço** (opcional na API): CEP → preenche logradouro/bairro/cidade/UF → usuário digita número e complemento (seção 11).
5. **Login**: e-mail e senha (regra da seção 3.3) + confirmação de senha (a confirmação é só do front).
6. `POST /coristas` com tudo junto (exemplo na seção 7.6). Resposta `201`: cadastro criado como **`PENDENTE`**. Mostre
   "Cadastro enviado. Aguarde a aprovação da liderança."
7. Enquanto `PENDENTE`, o login responde **403** com o motivo; depois de `APROVADO`, funciona.

> **Lacuna conhecida:** o cadastro público **não cria telefone do corista** (o endpoint de telefone exige login). O telefone do próprio
> corista é cadastrado **depois do primeiro login** (seção 6.2). Só o telefone do **responsável** vai no cadastro.

### 6.2 Meu perfil (corista)

- `GET /coristas/meu` → o cadastro do usuário logado (`pessoa.id` serve para telefone/endereço).
- Editar: `PUT /coristas/{id}` (id do corista) com o corpo **completo** (`CoristaRequest`). O corista **não altera a própria data de
  nascimento** (reenvie a mesma; se mudar vem 403). Pode mudar de congregação **só dentro do mesmo agrupamento**.
- Telefones: `/pessoas/{pessoaId}/telefones` (seção 7.9). Endereço: `PUT /enderecos/{id}` ou pelo próprio `PUT /coristas/{id}`.
- **Limite:** o próprio corista altera telefone e endereço **1 vez a cada 10 dias** (cada um conta separado). Mudou algo antes: **429**
  com a data em que libera. Reenviar o mesmo endereço sem mudança não conta. Líderes não têm esse limite.

### 6.3 Aprovação de cadastros (liderança)

1. `GET /coristas?status=PENDENTE` (paginado) — a lista já vem restrita ao seu recorte e agrupamento.
2. Detalhe: `GET /coristas/{id}`.
3. `PATCH /coristas/{id}/aprovar` ou `/reprovar` (sem corpo; o autor é o usuário logado). Resposta: `CoristaResponse` atualizado.
4. Só vale a partir de `PENDENTE` (senão 409). Quem aprova o quê: adolescentes — `DIRIGENTE_UNIAO` e líderes; jovens —
   `LIDERANCA_GRUPO_JOVEM`, `DIRIGENTE_CAMPANHA` e líderes. A API recusa (404) o que estiver fora do recorte de quem chama.

### 6.4 Afastamento

- Quem só pode **solicitar**: `POST /coristas/{id}/solicitar-afastamento` `{ "motivo" }` → `201`. Só para quem está `APROVADO`, e só **uma
  solicitação pendente por pessoa** (409).
- Quem decide (`CORISTA_APROVAR_AFASTAMENTO`: `LIDER_MOCIDADE`, `PASTOR`): fila em `GET /solicitacoes-afastamento?status=PENDENTE`; `PATCH
  /solicitacoes-afastamento/{id}/aprovar` (a pessoa vira `AFASTADO` e perde o acesso na hora) ou `/negar`.
- Diretamente: `PATCH /coristas/{id}/afastar` e `/retornar-do-afastamento` (volta a `APROVADO`).
- Quem está `AFASTADO` **não consegue logar** (403 com o motivo).

### 6.5 Promoção (só LIDER_MOCIDADE)

`PATCH /coristas/{id}/promover`: adolescente → jovem, antes dos 18, **irreversível**. Peça confirmação na tela. 409 se já é jovem ou já tem 18+.

### 6.6 Administração (ADMIN / PASTOR_ADMIN)

- **Estrutura**: cadastrar/editar/inativar agrupamentos e subdivisões. Para o tipo `AREA` não há subdivisão manual. Acrescentar a Área 75?
  `POST /agrupamentos {"tipo":"AREA","numero":75}`. Retirar: `DELETE` (soft), só sem congregação ativa dentro (409 se houver).
- **Congregações**: quem tem `CONGREGACAO_GERENCIAR` cria/edita/move/inativa dentro dos **seus** agrupamentos (403 fora).
- **Usuários**: criar login para uma pessoa (`POST /usuarios`), com `roleIds` vindos de `GET /roles`. `ADMIN` e `PASTOR_ADMIN` só são
  concedidos pelo `ADMIN`; o `PASTOR_ADMIN` só concede `PASTOR`.
- **Escopo**: `GET`/`PUT /usuarios/{id}/agrupamentos`. Pastor: 0 a 2 (campo missionário sozinho); outros papéis: exatamente 1; ADMIN e
  PASTOR_ADMIN: nenhum (409 se tentar).
- **Papéis**: `ROLE_SOLICITAR` pede um papel novo (nasce `PENDENTE` e inativo) → `ROLE_APROVAR` decide → `ROLE_GERENCIAR_PERMISSOES`
  define as permissões dele (`PUT /roles/{id}/permissoes`).

### 6.7 Dashboard do pastor e mapa (futuro)

Hoje: cards com contagens (`totalElements`) por status/lista/congregação, filtrando pelos agrupamentos do pastor. O **mapa das
congregações da área** é do front (Leaflet + OpenStreetMap); o back ainda **não tem latitude/longitude** na congregação (seção 12).
Enquanto isso o endereço da congregação (`endereco`) pode ser geocodificado no front a partir do CEP/endereço.

---

## 7. Referência das rotas

Prefixo `/v1/api`. "Perm." é a permissão exigida (além de estar logado). "Público" = sem token.

### 7.1 Autenticação

| Método e rota | Perm. | Corpo → Resposta |
|---|---|---|
| `POST /auth/login` | Público | `LoginRequest` → `200 LoginResponse` · 401 · 403 (motivo) · 423 |
| `PUT /auth/senha` | logado | `{ senhaAtual, novaSenha }` → `204` (derruba tokens antigos) |
| `POST /auth/redefinicao-senha/solicitar` | Público | `{ email }` → `202` sempre |
| `POST /auth/redefinicao-senha/confirmar` | Público | `{ token, novaSenha }` → `204` · 400 se token inválido/expirado |

### 7.2 Agrupamentos

| Método e rota | Perm. | Corpo → Resposta |
|---|---|---|
| `GET /agrupamentos?tipo=&ativo=` | Público | → `200 Agrupamento[]` (ordenado por tipo, número, nome) |
| `GET /agrupamentos/{id}` | Público | → `Agrupamento` · 404 |
| `POST /agrupamentos` | `AGRUPAMENTO_GERENCIAR` | `AgrupamentoRequest` → `201 Agrupamento` · 400 (campo incompatível com o tipo) · 409 (número/nome duplicado) |
| `PUT /agrupamentos/{id}` | `AGRUPAMENTO_GERENCIAR` | `AgrupamentoRequest` → `Agrupamento` · o **tipo não muda** (400) |
| `DELETE /agrupamentos/{id}` | `AGRUPAMENTO_GERENCIAR` | → `204` (soft) · 409 se tem congregação ativa |
| `PATCH /agrupamentos/{id}/reativar` | `AGRUPAMENTO_GERENCIAR` | → `Agrupamento` |

Campos por tipo (a API recusa campo que não pertence ao tipo):

| `tipo` | Obrigatórios | Não pode ter |
|---|---|---|
| `CAMPO_MISSIONARIO` | `nome`, `pais` | `numero` |
| `FILIAL` | `nome` | `numero`, `pais` |
| `SETOR` | `numero`, `nome` | `pais` |
| `AREA` | `numero` | `nome`, `pais` |

Rótulo na tela: AREA → `Área {numero}` · SETOR → `Setor {numero} – {nome}` · FILIAL → `Filial {nome}` · CAMPO → `{nome} ({pais})`.

### 7.3 Subdivisões

| Método e rota | Perm. | Corpo → Resposta |
|---|---|---|
| `GET /agrupamentos/{agrupamentoId}/subdivisoes?ativo=` | Público | → `200 Subdivisao[]` |
| `GET /agrupamentos/{agrupamentoId}/subdivisoes/{id}` | Público | → `Subdivisao` · 404 |
| `POST /agrupamentos/{agrupamentoId}/subdivisoes` | `AGRUPAMENTO_GERENCIAR` | `SubdivisaoRequest` → `201` · 400 se o tipo for `AREA` (implícita) · 409 duplicado / agrupamento inativo |
| `PUT /agrupamentos/{agrupamentoId}/subdivisoes/{id}` | `AGRUPAMENTO_GERENCIAR` | `SubdivisaoRequest` → `Subdivisao` |
| `DELETE /agrupamentos/{agrupamentoId}/subdivisoes/{id}` | `AGRUPAMENTO_GERENCIAR` | → `204` (soft) · 409 se tem congregação ativa |
| `PATCH /agrupamentos/{agrupamentoId}/subdivisoes/{id}/reativar` | `AGRUPAMENTO_GERENCIAR` | → `Subdivisao` |

Campo missionário: a subdivisão tem só `nome` (Local). Filial e setor: só `numero` (Área). Tipo `AREA`: não se cria à mão.

### 7.4 Congregações

| Método e rota | Perm. | Corpo → Resposta |
|---|---|---|
| `GET /congregacoes?agrupamentoId=&subdivisaoId=&ativa=` | Público | → `200 Congregacao[]` ordenado por nome |
| `GET /congregacoes/{id}` | Público | → `Congregacao` · 404 |
| `POST /congregacoes` | `CONGREGACAO_GERENCIAR` | `CongregacaoRequest` → `201` · 403 fora do escopo · 409 nome duplicado na subdivisão / subdivisão inativa |
| `PUT /congregacoes/{id}` | `CONGREGACAO_GERENCIAR` | `CongregacaoRequest` → `Congregacao`. Mudar `subdivisaoId` **remaneja** (destino ativo e no seu escopo). `endereco` ausente mantém o atual |
| `DELETE /congregacoes/{id}` | `CONGREGACAO_GERENCIAR` | → `204` (soft) |
| `PATCH /congregacoes/{id}/reativar` | `CONGREGACAO_GERENCIAR` | → `Congregacao` · 409 se subdivisão/agrupamento inativo |

### 7.5 Pessoas

| Método e rota | Perm. | Corpo → Resposta |
|---|---|---|
| `POST /pessoas` | `PESSOA_CRIAR` | `PessoaRequest` → `201 Pessoa` (nasce `PENDENTE`) |
| `GET /pessoas?nome=&agrupamentoId=&subdivisaoId=&congregacaoId=&faixaEtaria=&status=&page=&size=` | `PESSOA_LER` | → página de `Pessoa` |
| `GET /pessoas/{id}` | `PESSOA_LER` | → `Pessoa` · 404 |
| `PUT /pessoas/{id}` | `PESSOA_ATUALIZAR` | `PessoaRequest` → `Pessoa` (status e trilha não mudam aqui) |
| `DELETE /pessoas/{id}` | `PESSOA_INATIVAR` | → `204` (vira `INATIVO`) |
| `PATCH /pessoas/{id}/aprovar` | `PESSOA_APROVAR` | → `Pessoa` · 409 se não está `PENDENTE` |
| `PATCH /pessoas/{id}/reprovar` | `PESSOA_REPROVAR` | → `Pessoa` · 409 se não está `PENDENTE` |
| `PATCH /pessoas/{id}/afastar` | `CORISTA_APROVAR_AFASTAMENTO` | → `Pessoa` · 409 se não está `APROVADO` |
| `PATCH /pessoas/{id}/retornar-do-afastamento` | `CORISTA_APROVAR_AFASTAMENTO` | → `Pessoa` · 409 se não está `AFASTADO` |

O filtro `nome` é por **trecho**, sem diferenciar maiúsculas; `%` e `_` são literais.

### 7.6 Coristas

| Método e rota | Perm. | Corpo → Resposta |
|---|---|---|
| `POST /coristas` | **Público** | `CoristaRequest` (com `usuario` = auto-cadastro) → `201 Corista` (`PENDENTE`) |
| `GET /coristas?nome=&agrupamentoId=&subdivisaoId=&congregacaoId=&listaClassificacao=&status=&page=&size=` | `CORISTA_LER` | → página de `Corista` (só a lista do seu papel) |
| `GET /coristas/meu` | logado | → `Corista` do usuário logado · 404 se não é corista |
| `GET /coristas/{id}` | `CORISTA_LER` **ou** é o próprio | → `Corista` · 404 fora do recorte/escopo |
| `PUT /coristas/{id}` | `CORISTA_ATUALIZAR` + (`CORISTA_LER` **ou** é o próprio) | `CoristaRequest` → `Corista` · 403 se tentar mudar o nascimento sem `CORISTA_CORRIGIR_NASCIMENTO` |
| `PATCH /coristas/{id}/voz` | `CORISTA_ATUALIZAR_VOZ` | `{ tipoVoz }` → `Corista` |
| `DELETE /coristas/{id}` | `CORISTA_INATIVAR` | → `204` |
| `PATCH /coristas/{id}/aprovar` | `CORISTA_APROVAR` | → `Corista` |
| `PATCH /coristas/{id}/reprovar` | `CORISTA_REPROVAR` | → `Corista` |
| `PATCH /coristas/{id}/promover` | `CORISTA_PROMOVER` | → `Corista` · 409 |
| `PATCH /coristas/{id}/afastar` | `CORISTA_APROVAR_AFASTAMENTO` | → `Corista` |
| `PATCH /coristas/{id}/retornar-do-afastamento` | `CORISTA_APROVAR_AFASTAMENTO` | → `Corista` |

Exemplo de **auto-cadastro** (jovem) e de **menor** (note `responsavelLegal*` e `consentimentoLgpd`):

```jsonc
// POST /coristas  (público)
{
  "pessoa": {
    "nome": "Maria da Silva",
    "dataNascimento": "2001-04-12",
    "congregacaoId": "7f0c...-uuid",
    "endereco": {
      "logradouro": "Rua da Aurora", "numero": "123", "complemento": "Apto 2",
      "bairro": "Boa Vista", "cidade": "Recife", "uf": "PE", "cep": "50050-000"
    }
  },
  "tipoVoz": "SOPRANO",
  "tamanhoCamisa": "M",
  "ocupacao": "Estudante",
  "usuario": { "email": "maria@exemplo.com", "senha": "SenhaForte@123" }
}

// menor de 18: acrescente em "pessoa"
//   "responsavelLegalNome": "Joana da Silva",
//   "responsavelLegalTelefone": "(81) 99999-0000",
//   "consentimentoLgpd": true
```

No `PUT`, o campo `usuario` é **ignorado**. Quem está desligado (virou líder) some das listas.

### 7.7 Solicitações de afastamento

| Método e rota | Perm. | Corpo → Resposta |
|---|---|---|
| `POST /coristas/{id}/solicitar-afastamento` | `CORISTA_SOLICITAR_AFASTAMENTO` | `{ motivo }` (1–500) → `201 SolicitacaoAfastamento` · 404 fora do recorte · 409 (não aprovado / já há pendente) |
| `GET /solicitacoes-afastamento?status=&page=&size=` | `CORISTA_APROVAR_AFASTAMENTO` | → página (mais antigas primeiro) |
| `PATCH /solicitacoes-afastamento/{id}/aprovar` | `CORISTA_APROVAR_AFASTAMENTO` | → `SolicitacaoAfastamento` (a pessoa vira `AFASTADO`) · 409 se já decidido |
| `PATCH /solicitacoes-afastamento/{id}/negar` | `CORISTA_APROVAR_AFASTAMENTO` | → `SolicitacaoAfastamento` · 409 se já decidido |

### 7.8 Endereços

A API de endereço **guarda** o endereço; a consulta de CEP é do front (seção 11).

| Método e rota | Perm. | Corpo → Resposta |
|---|---|---|
| `POST /enderecos` | `PESSOA_ATUALIZAR` | `EnderecoRequest` → `201 Endereco` (endereço solto; para vincular, use o `endereco` aninhado em pessoa/congregação) |
| `GET /enderecos/{id}` | `PESSOA_LER` **ou** é o meu | → `Endereco` · 404 fora do escopo |
| `PUT /enderecos/{id}` | `PESSOA_ATUALIZAR` **ou** é o meu | `EnderecoRequest` → `Endereco` · 429 (limite de 10 dias do próprio corista) |

**Jeito normal de gravar endereço:** enviar `endereco` **dentro** de `pessoa` (cadastro/edição de corista ou pessoa) ou de congregação. Na
edição, `endereco` ausente **mantém** o atual; enviado, atualiza o existente (ou cria, se a pessoa ainda não tinha).

### 7.9 Telefones

Rotas aninhadas na pessoa: `/pessoas/{pessoaId}/telefones`. Perm.: leitura = `PESSOA_LER` **ou** a própria pessoa; escrita = `PESSOA_ATUALIZAR` **ou** a própria pessoa.

| Método e rota | Corpo → Resposta |
|---|---|
| `POST /pessoas/{pessoaId}/telefones` | `TelefoneRequest` → `201 Telefone` · 409 número repetido · 429 (limite de 10 dias do próprio corista) |
| `GET /pessoas/{pessoaId}/telefones` | → `Telefone[]` (principal primeiro) |
| `GET /pessoas/{pessoaId}/telefones/{id}` | → `Telefone` |
| `PUT /pessoas/{pessoaId}/telefones/{id}` | `TelefoneRequest` → `Telefone` |
| `DELETE /pessoas/{pessoaId}/telefones/{id}` | → `204` (se era o principal, o mais antigo assume) |

O **primeiro** telefone da pessoa é sempre o principal; `principal: true` troca o principal; `false`/ausente não rebaixa.

### 7.10 Usuários (logins) e escopo

| Método e rota | Perm. | Corpo → Resposta |
|---|---|---|
| `POST /usuarios` | `USUARIO_GERENCIAR` | `UsuarioRequest` → `201 Usuario` · 409 (e-mail repetido / pessoa já tem login / papel inválido) · 403 (papel global) |
| `GET /usuarios?ativo=&page=&size=` | `USUARIO_GERENCIAR` | → página de `Usuario` (só os do seu escopo) |
| `GET /usuarios/{id}` | `USUARIO_GERENCIAR` | → `Usuario` · 404 |
| `PUT /usuarios/{id}` | `USUARIO_GERENCIAR` | `UsuarioRequest` (**substitui tudo, inclusive a senha**; a pessoa não muda) → `Usuario` |
| `DELETE /usuarios/{id}` | `USUARIO_GERENCIAR` | → `204` (desativa; o acesso cai na hora) |
| `PATCH /usuarios/{id}/reativar` | `USUARIO_GERENCIAR` | → `Usuario` |
| `GET /usuarios/meu/agrupamentos` | logado | → `Escopo` do usuário logado |
| `GET /usuarios/{id}/agrupamentos` | `ESCOPO_GERENCIAR` | → `Escopo` |
| `PUT /usuarios/{id}/agrupamentos` | `ESCOPO_GERENCIAR` | `{ agrupamentoIds: [] }` (substitui o conjunto) → `Escopo` · 409 se violar a regra do papel |

### 7.11 Papéis e permissões

| Método e rota | Perm. | Corpo → Resposta |
|---|---|---|
| `GET /roles?ativo=&status=` | logado | → `Role[]` |
| `GET /roles/{id}` | logado | → `Role` |
| `POST /roles` | `ROLE_GERENCIAR_PERMISSOES` | `{ nome, descricao }` → `201 Role` |
| `POST /roles/solicitacoes` | `ROLE_SOLICITAR` | `{ nome, descricao }` → `201 Role` (`PENDENTE`, inativo) |
| `PATCH /roles/{id}/aprovar` · `/reprovar` | `ROLE_APROVAR` | → `Role` · 409 se não está pendente |
| `PUT /roles/{id}` | `ROLE_GERENCIAR_PERMISSOES` | `{ nome, descricao }` → `Role` |
| `DELETE /roles/{id}` · `PATCH /roles/{id}/reativar` | `ROLE_GERENCIAR_PERMISSOES` | `204` / `Role` |
| `GET /roles/{id}/permissoes` | `ROLE_GERENCIAR_PERMISSOES` | → `{ roleId, nome, permissoes: string[] }` |
| `PUT /roles/{id}/permissoes` | `ROLE_GERENCIAR_PERMISSOES` | `{ permissoes: string[] }` (substitui todas) → `{ roleId, nome, permissoes }` · 400 permissão inexistente · 409 se for `ADMIN` ou `PASTOR_ADMIN` (fixos) |

---

## 8. Tipos TypeScript

> Gere também do OpenAPI (`/v3/api-docs`); estes tipos são o espelho do que a API devolve hoje.

```ts
type UUID = string;
type ISODate = string;      // "2000-05-17"
type ISOInstant = string;   // "2026-10-11T12:00:00Z"

type TipoAgrupamento = 'CAMPO_MISSIONARIO' | 'FILIAL' | 'SETOR' | 'AREA';
type StatusPessoa = 'PENDENTE' | 'APROVADO' | 'REPROVADO' | 'AFASTADO' | 'INATIVO';
type FaixaEtaria = 'ADOLESCENTE' | 'JOVEM';
type ListaClassificacao = 'ADOLESCENTE' | 'JOVEM';
type TipoVoz = 'BAIXO' | 'CONTRALTO' | 'SOPRANO' | 'TENOR';
type TamanhoCamisa = 'PP' | 'P' | 'M' | 'G' | 'GG' | 'XGG';
type StatusSolicitacao = 'PENDENTE' | 'APROVADA' | 'NEGADA';
type StatusRole = 'PENDENTE' | 'APROVADA' | 'REPROVADA';

interface Page<T> { content: T[]; page: { size: number; number: number; totalElements: number; totalPages: number } }

interface Problema { type: string; title: string; status: number; detail: string; instance?: string }

// ---------- autenticação ----------
interface LoginRequest { email: string; senha: string }
interface LoginResponse { token: string; email: string; roles: string[] }  // ROLE_* e permissões misturados

// ---------- hierarquia ----------
interface AgrupamentoRequest { tipo: TipoAgrupamento; nome?: string; numero?: number; pais?: string }
interface Agrupamento { id: UUID; tipo: TipoAgrupamento; nome: string | null; numero: number | null; pais: string | null;
                        ativo: boolean; criadoEm: ISOInstant; atualizadoEm: ISOInstant }
interface SubdivisaoRequest { numero?: number; nome?: string }
interface Subdivisao { id: UUID; agrupamentoId: UUID; numero: number | null; nome: string | null; ativo: boolean;
                       criadoEm: ISOInstant; atualizadoEm: ISOInstant }

// ---------- endereço ----------
interface EnderecoRequest { logradouro: string; numero?: string; complemento?: string; bairro: string; cidade: string; uf: string; cep?: string }
interface Endereco { id: UUID; logradouro: string; numero: string | null; complemento: string | null; bairro: string;
                     cidade: string; uf: string; cep: string | null; criadoEm: ISOInstant; atualizadoEm: ISOInstant }

// ---------- congregação ----------
interface CongregacaoRequest { subdivisaoId: UUID; nome: string; endereco?: EnderecoRequest }
interface Congregacao { id: UUID; agrupamento: Agrupamento; subdivisao: Subdivisao; nome: string; ativa: boolean;
                        endereco: Endereco | null; criadoEm: ISOInstant; atualizadoEm: ISOInstant }
interface CongregacaoResumo { id: UUID; nome: string; ativa: boolean; agrupamento: Agrupamento; subdivisao: Subdivisao }

// ---------- pessoa e corista ----------
interface PessoaRequest { nome: string; dataNascimento: ISODate; congregacaoId: UUID; endereco?: EnderecoRequest;
                          responsavelLegalNome?: string; responsavelLegalTelefone?: string; consentimentoLgpd?: boolean }
interface Pessoa { id: UUID; nome: string; nomeExibicao: string; dataNascimento: ISODate; idade: number; faixaEtaria: FaixaEtaria;
                   status: StatusPessoa; congregacao: CongregacaoResumo; endereco: Endereco | null;
                   responsavelLegalNome: string | null; responsavelLegalTelefone: string | null; consentimentoLgpdEm: ISOInstant | null;
                   aprovadoPor: UUID | null; aprovadoEm: ISOInstant | null; reprovadoPor: UUID | null; reprovadoEm: ISOInstant | null;
                   criadoEm: ISOInstant; atualizadoEm: ISOInstant }
interface CoristaRequest { pessoa: PessoaRequest; tipoVoz: TipoVoz; tamanhoCamisa: TamanhoCamisa; ocupacao?: string;
                           usuario?: { email: string; senha: string } }   // "usuario" só no auto-cadastro (POST)
interface Corista { id: UUID; pessoa: Pessoa; tipoVoz: TipoVoz; tamanhoCamisa: TamanhoCamisa; ocupacao: string | null;
                    listaClassificacao: ListaClassificacao; promovidoPor: UUID | null; promovidoEm: ISOInstant | null;
                    criadoEm: ISOInstant; atualizadoEm: ISOInstant }

// ---------- telefone ----------
interface TelefoneRequest { numero: string; whatsapp?: boolean; principal?: boolean }
interface Telefone { id: UUID; pessoaId: UUID; numero: string; whatsapp: boolean; principal: boolean; criadoEm: ISOInstant; atualizadoEm: ISOInstant }

// ---------- afastamento ----------
interface SolicitacaoAfastamento { id: UUID; pessoaId: UUID; pessoaNome: string; motivo: string; status: StatusSolicitacao;
                                   solicitadoPor: UUID; solicitadoEm: ISOInstant; decididoPor: UUID | null; decididoEm: ISOInstant | null }

// ---------- usuário, escopo, papéis ----------
interface UsuarioRequest { pessoaId: UUID; email: string; senha: string; roleIds: UUID[] }
interface Usuario { id: UUID; pessoaId: UUID; pessoaNome: string; email: string; ativo: boolean; roles: string[]; criadoEm: ISOInstant; atualizadoEm: ISOInstant }
interface Escopo { usuarioId: UUID; global: boolean; agrupamentos: Agrupamento[] }
interface Role { id: UUID; nome: string; descricao: string; ativo: boolean; status: StatusRole; solicitadoPor: UUID | null;
                 decididoPor: UUID | null; decididoEm: ISOInstant | null; criadoEm: ISOInstant; atualizadoEm: ISOInstant }
```

> Os campos `criadoEm`/`atualizadoEm` podem vir `null` em respostas de criação imediata; trate como opcionais na exibição.

---

## 9. Enumerações

| Enum | Valores | Rótulo sugerido |
|---|---|---|
| `TipoAgrupamento` | `CAMPO_MISSIONARIO`, `FILIAL`, `SETOR`, `AREA` | Campo Missionário, Filial, Setor, Área |
| `StatusPessoa` | `PENDENTE`, `APROVADO`, `REPROVADO`, `AFASTADO`, `INATIVO` | Pendente, Aprovado, Reprovado, Afastado, Inativo |
| `FaixaEtaria` / `ListaClassificacao` | `ADOLESCENTE`, `JOVEM` | Adolescente, Jovem |
| `TipoVoz` | `BAIXO`, `CONTRALTO`, `SOPRANO`, `TENOR` | Baixo, Contralto, Soprano, Tenor |
| `TamanhoCamisa` | `PP`, `P`, `M`, `G`, `GG`, `XGG` | PP … XGG |
| `StatusSolicitacao` | `PENDENTE`, `APROVADA`, `NEGADA` | Pendente, Aprovada, Negada |
| `StatusRole` | `PENDENTE`, `APROVADA`, `REPROVADA` | Pendente, Aprovada, Reprovada |

Um valor de enum inexistente no corpo devolve **400**.

---

## 10. Validações para repetir no front

A API valida tudo, mas a resposta de erro de campo é genérica (seção 2.2). Valide antes de enviar:

| Campo | Regra |
|---|---|
| `email` | obrigatório, formato de e-mail, até 150 |
| `senha` / `novaSenha` | 8–100, com maiúscula, minúscula, número e caractere especial |
| `pessoa.nome` | obrigatório, até 150 |
| `pessoa.dataNascimento` | obrigatória, **no passado** |
| `pessoa.congregacaoId` | obrigatório |
| Menor de 18 | `responsavelLegalNome` **e** `responsavelLegalTelefone` obrigatórios; `consentimentoLgpd = true` |
| `responsavelLegalNome` | até 150; **nome e telefone vêm juntos** (um sem o outro é 400) |
| Telefones (`numero`, `responsavelLegalTelefone`) | `^\(?\d{2}\)?\s?9?\d{4}-?\d{4}$` — aceita `81999990000`, `(81) 99999-0000`, `8133334444`; a API grava só os dígitos |
| `endereco.logradouro` | obrigatório, até 150 |
| `endereco.numero` | opcional, até 10 |
| `endereco.complemento` | opcional, até 100 |
| `endereco.bairro` / `cidade` | obrigatórios, até 100 |
| `endereco.uf` | obrigatória, 2 letras (a API grava em maiúsculas) |
| `endereco.cep` | **opcional** (zona rural pode não ter): `00000-000` ou `00000000`; a API grava só dígitos |
| `tipoVoz`, `tamanhoCamisa` | obrigatórios (enums) |
| `ocupacao` | opcional, até 100 |
| `motivo` (afastamento) | obrigatório, até 500 |
| `agrupamento` | campos por tipo (seção 7.2); `numero` > 0; `nome` até 150; `pais` até 100 |
| `subdivisao` | `numero` > 0; `nome` até 150 (regra por tipo, seção 7.3) |
| `congregacao.nome` | obrigatório, até 150 |
| `role.nome` | até 50; `descricao` até 255 |

Idade: calcule pela `dataNascimento` — **menor de 18** vale até 17 anos, 11 meses e 30 dias.

---

## 11. Endereço e CEP

A API **não consulta CEP**. O desenho é: o **front consulta o CEP**, preenche o formulário, o usuário completa número e complemento, e o
front envia o endereço completo para a API gravar. O endereço sempre vai **aninhado** (`pessoa.endereco`, `congregacao.endereco`) ou, solto, em
`POST /enderecos`.

**Sugestão de consulta (ViaCEP, gratuita e pública, não faz parte da API):**

```ts
async function buscarCep(cep: string) {
  const digitos = cep.replace(/\D/g, '');
  if (digitos.length !== 8) return null;
  const r = await fetch(`https://viacep.com.br/ws/${digitos}/json/`);
  const d = await r.json();
  if (d.erro) return null;                       // CEP não encontrado
  return { logradouro: d.logradouro, bairro: d.bairro, cidade: d.localidade, uf: d.uf, cep: d.cep };
}
```

Mapa de campos: ViaCEP `logradouro`→`logradouro`, `bairro`→`bairro`, **`localidade`→`cidade`**, `uf`→`uf`, `cep`→`cep`. O `numero` e o `complemento`
são digitados (o ViaCEP não os fornece). Boas práticas: buscar ao completar 8 dígitos (ou ao sair do campo), mostrar "carregando", permitir
**preenchimento manual** se o CEP falhar ou não existir, e deixar `logradouro`/`bairro` editáveis (CEP de cidade pequena vem sem eles).

Formatação: a API devolve o `cep` **só com dígitos** (`50050000`): formate `00000-000` na tela. Telefone também volta só com dígitos.

Regras de edição: o corista pode mudar o **próprio** endereço no máximo **1 vez a cada 10 dias** (429 com a data). Reenviar o mesmo endereço
não conta. Líderes editando a ficha de outra pessoa não têm limite.

---

## 12. Pendências do back que afetam o front

| Pendência | Efeito no front |
|---|---|
| **CORS não configurado** | Em dev, usar proxy do dev-server (seção 2.3). Será resolvido junto do Auth0. |
| Erro de validação **sem lista de campos** | O front precisa validar sozinho (seção 10) e já tratar o campo `errors` (formato-alvo na seção 2.2). Melhoria prevista no back: devolver `errors: [{campo, mensagem}]` (padrão Problem Details). |
| Telefone do corista **não vai no auto-cadastro** | Cadastrar o telefone depois do primeiro login. |
| **Envio de e-mail** da redefinição de senha não existe em produção | Tela pronta, mas só funciona em prod depois do e-mail (ou do Auth0). |
| Sem **refresh token** | Sessão de 60 min; ao 401, pedir login de novo. |
| Sem endpoints de **dashboard** | Usar `page.totalElements` com `size=1`. |
| **Mapa**: congregação sem latitude/longitude | Geocodificar no front por ora. |
| **Auditoria de leitura** do `PASTOR_ADMIN` (LGPD) | Ainda não existe. |
| Limite de tentativas no `POST /coristas` público | Sem proteção anti-abuso ainda. |
| Cadastro de **Auth0** | Seção 13. |

---

## 13. Auth0 (próximo passo do back)

> **Decisão registrada:** o **próximo passo no back é integrar o Auth0.** Esta seção descreve o plano e o que ele muda para o front. Ainda
> **não está implementado**; o front de hoje continua usando `POST /auth/login`.

### 13.1 O que muda (visão)

- O login passa a ser feito pelo **Auth0** (Universal Login, fluxo *Authorization Code + PKCE*). O front usa o SDK do Auth0 para SPA
  (`@auth0/auth0-react` ou `@auth0/auth0-spa-js`) e envia o **access token do Auth0** em `Authorization: Bearer ...`.
- A API passa a ser um **resource server**: valida o JWT do Auth0 (emissor, *audience*, assinatura por JWKS) em vez de emitir o próprio.
- Continuam **no back**: o cadastro (`Usuario`/`Pessoa`/`Corista`), o status (`PENDENTE`/`APROVADO`/`AFASTADO`...), os papéis, as permissões e o
  escopo por agrupamento. O Auth0 diz **quem é** o usuário; o back continua dizendo **o que ele pode**.
- O vínculo entre o usuário do Auth0 e o `Usuario` do sistema será pelo e-mail e/ou pelo `sub` do token (a definir).

### 13.2 O que sai do front quando o Auth0 entrar

- Telas de login, "esqueci a senha" e "trocar senha" próprias (viram o fluxo do Auth0): `POST /auth/login`, `PUT /auth/senha` e
  `/auth/redefinicao-senha/*` devem ser descontinuados. O bloqueio por 3 senhas erradas e o e-mail de redefinição passam a ser do Auth0
  (resolvendo duas pendências da seção 12).
- Guardar o token manualmente: o SDK cuida (e renova de forma silenciosa, o que resolve a falta de refresh token).

### 13.3 O que continua igual

Todas as rotas de negócio (`/coristas`, `/pessoas`, `/agrupamentos`, ...), os formatos de erro, o escopo, as permissões. O front só troca **como obtém o token**.
Quem ainda não tem acesso (cadastro `PENDENTE`, `AFASTADO`...) continuará recebendo **403 com o motivo** nas chamadas da API.

### 13.4 Como se preparar agora

1. **Um único módulo de autenticação** no front (ex.: `auth/`): expõe `entrar()`, `sair()`, `obterToken()`, `usuario` e `permissoes`.
   Nenhum outro arquivo lê token ou chama `/auth/*`.
2. Um **interceptor HTTP** que pede o token a esse módulo e anexa o `Authorization`, e trata `401` em um lugar só.
3. Decidir telas por **permissões**, não por papéis; hoje elas vêm do `roles` do login e, no futuro, de uma chamada de "quem sou eu" no back
   (a definir junto do Auth0). Encapsule isso atrás de `temPermissao('CORISTA_APROVAR')`.
4. Não espalhar suposições sobre o formato do token.

### 13.5 Decisões em aberto (a fechar no back antes do front migrar)

- Como o **auto-cadastro** cria o usuário no Auth0 (o back cria via Management API, ou o usuário se registra no Auth0 e depois completa o perfil).
- Como o back **entrega as permissões** ao front (claim customizada no token, ou rota "quem sou eu").
- O que acontece com os logins já existentes (migração de usuários/senhas).
- Bloqueio de acesso de quem está `PENDENTE`/`AFASTADO`/`INATIVO` (rejeitar no back com 403, como hoje, ou também no Auth0).
- Login social (Google etc.) ou só e-mail/senha; MFA.
- Ambientes (tenants) de dev e prod e as URLs permitidas (callback/logout/CORS).

---

## 14. Checklist de implementação

- [ ] Cliente HTTP com base URL configurável, `Authorization` automático, interceptor de 401 e tradução de `Problema.detail`.
- [ ] Proxy de dev para contornar o CORS (seção 2.3).
- [ ] Módulo único de autenticação (seção 13.4).
- [ ] Login + tratamento de 401/403/423; guardar `roles` (permissões) do login.
- [ ] Guardas de rota e menu por **permissão** (seção 4.3).
- [ ] Formulário de auto-cadastro com a cascata tipo → agrupamento → subdivisão (pular no tipo `AREA`) → congregação.
- [ ] Menor de idade: responsável + consentimento LGPD.
- [ ] Endereço: busca de CEP (ViaCEP), preenchimento manual de reserva, CEP opcional.
- [ ] Meu perfil (`/coristas/meu`), edição, telefones, limite de 10 dias (429).
- [ ] Listas paginadas com filtros (`nome`, `status`, `listaClassificacao`, `agrupamentoId`, `subdivisaoId`, `congregacaoId`).
- [ ] Aprovação/reprovação de cadastros, promoção (com confirmação), afastamento (pedido e fila).
- [ ] Administração: agrupamentos, subdivisões, congregações, usuários, escopo, papéis e permissões.
- [ ] Dashboard por contagens (`size=1` + `totalElements`) e, depois, o mapa.
- [ ] Nunca enviar o autor das ações; nunca decidir segurança só no front.
- [ ] Tipos gerados do OpenAPI (`/v3/api-docs`) ou os da seção 8.
