# registro-coristas-api

API de gestão da mocidade (coristas) da **Assembleia de Deus em Pernambuco (IEAD-PE)**, com hierarquia
**Área → Congregação → Pessoa → (Corista)** e controle de acesso baseado em papéis (RBAC).

A IEAD-PE tem 74 áreas (matriz = Área 1), cada uma com N congregações. O MVP cobre apenas a **Área 40**
(13 congregações), mas o modelo de dados já nasce multi-área.

## Stack

- **Java 21** + **Spring Boot 4.1**
- **PostgreSQL 16** (via Docker)
- **Spring Data JPA / Hibernate**
- **Flyway** (migrations versionadas — `spring-boot-starter-flyway` + `flyway-database-postgresql`)
- **Bean Validation** (`spring-boot-starter-validation`)
- **springdoc-openapi** (Swagger UI)
- **Lombok**
- **JUnit 5 + Mockito** (testes de service) e **Testcontainers PostgreSQL** (testes de integração)

> Autenticação/autorização (Spring Security + JWT) ainda **não** foi implementada — ver [Roadmap](#roadmap).

## Arquitetura

MVC clássico em camadas (`Controller → Service → Repository → DB`), organizado **por feature/domínio**
(`area`, `congregacao`, `pessoa`, `corista`, `telefone`, `endereco`, `evento`, `role`, `usuario`...), e dentro
de cada pacote a divisão técnica (`controller` / `service` / `repository` / `model` / `dto` / `exception`).

Regras:
- DTOs (Records, quando possível) na camada de apresentação — entidades JPA nunca são expostas diretamente na API.
- Validações de negócio ficam exclusivamente na camada `Service`.
- Erros tratados globalmente via `@ControllerAdvice`, no formato **Problem Details (RFC 7807)**.
- IDs em `UUID` (`gen_random_uuid()`).

## Modelo de domínio

Uma pessoa é representada **uma única vez**. Papéis são vínculos, não tabelas duplicadas.

```
Apoio / Maestro / Pastor:      AppUser → Pessoa
Corista (adolescente/jovem):   AppUser → Pessoa → Corista
```

- **`Pessoa`**: dados comuns a todos (nome, data de nascimento, endereço, telefone, congregação, status).
- **`Corista`**: especialização **1:1** de `Pessoa` (FK `pessoa_id UNIQUE`), com os campos exclusivos de
  corista (tipo de voz, tamanho de camisa, ocupação, lista de classificação etc.).
- Nem toda `Pessoa` tem `AppUser` (ex.: menor gerenciado pelo líder). Todo `AppUser` aponta para uma `Pessoa`.
- Não existe entidade `Membro` única, nem corista como tabela solta que duplica dados de pessoa.

### Faixa etária e classificação

- **Faixa etária real** é derivada da data de nascimento (nunca gravada): adolescente até 17a 11m 30d,
  jovem a partir de 18a.
- **`lista_classificacao`** (`ADOLESCENTE` | `JOVEM`) é o campo gravado em `Corista` e, por padrão, segue a idade.
- **Promoção antecipada**: apenas o `LIDER_MOCIDADE` pode promover um adolescente para a lista de jovens.
  É permanente e registra trilha (`promovido_por`, `promovido_em`). Um menor promovido continua menor —
  as proteções LGPD continuam valendo.

### Workflow de cadastro

`PENDENTE` (self-service) → aprovação por `PRESBITERO` (ou papel superior) → `APROVADO`. Remoção é sempre
**soft delete** (status `INATIVO`), nunca `DELETE` físico.

### LGPD

Cadastro de menores exige responsável legal (nome + telefone), consentimento explícito e trilha de
aprovação/alteração.

## Endpoints (v1)

Prefixo comum: `/v1/api`.

| Recurso | Endpoints |
|---|---|
| **Área** | `POST /areas` · `GET /areas` · `GET /areas/{id}` · `PUT /areas/{id}` · `DELETE /areas/{id}` (soft) · `PATCH /areas/{id}/reativar` |
| **Congregação** | `POST /congregacoes` · `GET /congregacoes` · `GET /congregacoes/{id}` · `PUT /congregacoes/{id}` · `DELETE /congregacoes/{id}` (soft) · `PATCH /congregacoes/{id}/reativar` |
| **Pessoa** | `POST /pessoas` · `GET /pessoas` (paginado, filtros: nome, areaId, congregacaoId, faixaEtaria, status) · `GET /pessoas/{id}` · `PUT /pessoas/{id}` · `DELETE /pessoas/{id}` (soft → `INATIVO`) |
| **Corista** | `POST /coristas` · `GET /coristas` (paginado, filtros: nome, areaId, congregacaoId, listaClassificacao, status) · `GET /coristas/{id}` · `PUT /coristas/{id}` · `DELETE /coristas/{id}` (soft → `INATIVO`) |
| **Endereço** | `POST /enderecos` · `GET /enderecos/{id}` · `PUT /enderecos/{id}` |
| **Telefone** | `POST /pessoas/{pessoaId}/telefones` · `GET /pessoas/{pessoaId}/telefones` · `GET .../telefones/{id}` · `PUT .../telefones/{id}` · `DELETE .../telefones/{id}` |
| **Role** | `POST /roles` · `GET /roles` · `GET /roles/{id}` · `PUT /roles/{id}` · `DELETE /roles/{id}` (soft) · `PATCH /roles/{id}/reativar` |

Documentação interativa (Swagger UI) disponível em `/swagger-ui.html` no perfil `dev` (desabilitada em `prod`).

## Como rodar

### Pré-requisitos

- JDK 21
- Docker (para o Postgres)

### 1. Configurar variáveis de ambiente

```bash
cp .env.example .env
# ajuste os valores se necessário (defaults já funcionam para dev local)
```

### 2. Subir o banco

```bash
docker compose up -d
```

### 3. Rodar a aplicação

```bash
./mvnw spring-boot:run
```

A API sobe em `http://localhost:8080`. Migrations Flyway rodam automaticamente na inicialização.

### Build

```bash
./mvnw clean install
```

### Rodar tudo (banco + app) via Docker

```bash
docker compose --profile app up --build
```

(`docker compose up -d`, sem `--profile app`, sobe **só** o banco.)

## Testes

```bash
./mvnw test
```

- Testes unitários de `Service` (JUnit 5 + Mockito).
- Testes de integração de endpoints com Spring Boot Test.
- Banco de teste via **Testcontainers PostgreSQL** — H2 é evitado de propósito, pois o projeto depende de
  comportamentos específicos do Postgres.
- Nenhuma funcionalidade nova é integrada sem teste mínimo do `Service` correspondente.

## Perfis (Spring Profiles)

- **default (dev)**: usado quando nenhum profile é ativado. Defaults seguros para rodar local
  (`DB_USER`/`DB_PASSWORD` com fallback `postgres`), `show-sql` ligado, Swagger habilitado.
- **prod** (`SPRING_PROFILES_ACTIVE=prod`, ativado no Render): sem defaults nas credenciais do banco
  (falha ao subir se a env var não existir), `ddl-auto` fixo em `validate`, `show-sql`/`format_sql`
  desligados, Swagger/OpenAPI desabilitado.

## Migrations

- Toda alteração de schema é feita via migration **Flyway** — nunca `ddl-auto: update`. Em dev/prod o
  Hibernate usa `ddl-auto: validate`.
- Migrations são versionadas e nunca editadas após aplicadas; correções viram uma nova migration.
- Ordem atual: `V1` endereço · `V2` área · `V3` congregação · `V4` pessoa · `V5` telefone · `V6` corista ·
  `V7` role · `V8`/`V9` ajustes de corista (tipo de voz, tamanho de camisa) · `V10` soft delete/trilha em role.

## Variáveis de ambiente

| Variável | Descrição | Default (dev) |
|---|---|---|
| `DB_HOST` | Host do Postgres | `localhost` |
| `DB_PORT` | Porta do Postgres | `5432` |
| `DB_NAME` | Nome do banco | `registro_coristas` |
| `DB_USER` | Usuário do banco | `postgres` |
| `DB_PASSWORD` | Senha do banco | `postgres` |
| `SPRING_PROFILES_ACTIVE` | Perfil ativo (`prod` em produção) | — |

Nunca commitar segredos. Em dev, use `.env` (ignorado no Git); em produção (Render), todas as variáveis
vêm da plataforma.

## Roadmap

- **Autenticação/autorização** (Spring Security + JWT): ainda não implementada. Papéis (`role`) já modelados
  no schema (`V7`/`V10`) e com CRUD próprio; RBAC vai validar **role** + **vínculo geográfico** (área/congregação)
  sempre no backend. Regra decidida: `ADMIN` acesso global · `PASTOR` até 2 áreas · demais papéis, exatamente
  1 área — via tabela de junção `user_area` (não coluna única em `AppUser`).
- Módulo de **eventos**: adiado para outra versão (fora do escopo do MVP por ora).
- Observabilidade: Actuator (health/readiness/liveness), Micrometer + Prometheus, Micrometer Tracing/OpenTelemetry,
  logs estruturados em JSON.

## Convenções de contribuição

- **GitHub Flow**: `main` sempre estável e deployável; tudo via Pull Request.
- **Branches**: `feat/<area>-<atividade>`, `fix/<...>`, `chore/<...>` — uma atividade por branch.
- **Commits**: [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `chore:`, `test:`, `docs:`, `refactor:`).
- **Definition of Done**: compila + testes verdes + PR revisado + documentação atualizada quando aplicável.
- Todo o código (classes, métodos, variáveis, tabelas e colunas) é escrito em **Português**.
