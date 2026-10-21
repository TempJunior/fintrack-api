# Fintrack — Estado atual do projeto

> Documento **vivo**. Atualize sempre que concluir uma etapa ou tomar uma decisão durante a implementação.
> Os documentos `fintrack-srs.md` e `fintrack-arquitetura.md` dizem **o que** construir; este diz **onde estamos**.

| Campo | Valor |
|---|---|
| Última atualização | 2026-10-20 |
| Versão do SRS | 0.3 |
| Versão da arquitetura | 0.3 |

Legenda: ⬜ não iniciado · 🟨 em andamento · ✅ pronto · ⏸ adiado · ⛔ bloqueado

---

## 1. Etapa atual
**Etapa 3 — Transações (núcleo)** (✅ 2026-10-20, branch `feat/etapa-3-transacoes`, aguardando commit).
- `TransacaoService.salvarNovas(conta, transacoes)` → `ResultadoDedup` (novas + duplicadas): ignora as já gravadas na conta e as repetidas no próprio arquivo; valida que todas são da conta (RF03).
- `TransacaoRepository.findDedupKeysExistentes`: uma consulta `IN` para o arquivo inteiro, em vez de uma por transação.
- `ImportServiceImpl` não acessa mais o `TransacaoRepository` (regra do "dono", arquitetura §3.3).
- Conferido com o `itau.ofx` real: 1ª importação grava 16, 2ª grava 0 (RNF04).
- 52 testes passando.

Etapas 1 e 2 concluídas (Etapa 1 sem o 1.3, adiado por falta de Docker).

## 2. Próximo passo
**Etapa 4 — Importação:** `FormatDetector`, `StatementParser` + `OfxStatementParser` (Strategy, ADR-002), resolver, upload multipart pelo `ImportController`, validação (checksum + conta declarada, D-09), atomicidade e auditoria em `importacoes` inclusive na falha (RF04–RF09, RNF05, RNF12, ADR-004). Resolve os itens 6, 7, 8 e 11 da §7.

Pendente da Etapa 1 (quando houver Docker): Postgres no docker compose, perfil apontando para ele e Testcontainers nos testes de integração.

---

## 3. Stack e dependências (mapeado em 2026-10-20)

| Item | Versão / valor | Observação |
|---|---|---|
| Java | 21 (`java.version` no pom) | JDK instalado na máquina: 25.0.1 |
| Spring Boot (parent) | 3.5.16 | |
| Build | Maven Wrapper 3.3.4 → Maven 3.9.16 | `mvnw` / `mvnw.cmd` |
| `spring-boot-starter-web` | (BOM) | `ContaController` |
| `spring-boot-starter-validation` | (BOM) | Bean Validation nos DTOs |
| `springdoc-openapi-starter-webmvc-ui` | 2.8.17 | Spec `/v3/api-docs`, UI `/swagger-ui.html` (fechar em prod na Etapa 8) |
| `spring-boot-starter-data-jpa` | (BOM) | Hibernate 6, `ddl-auto: validate`, `open-in-view: false` |
| `ofx4j` (`com.webcohesion.ofx4j`) | 1.39 | Parser OFX (SGML 1.x e XML 2.x) |
| `h2` | (BOM), runtime | Na memória, modo PostgreSQL, em `dev` e `test`. Console em `/h2-console` só no `dev` |
| `flyway-core` | (BOM) | Migrations em `src/main/resources/db/migration`; `V1__schema_inicial.sql` |
| `spring-boot-starter-test` | (BOM), test | JUnit 5 |

**Removido:** Lombok (2026-10-20, incremento 1.1 — não adotado, ver instruções).

**Ainda não presentes** (previstos no plano): driver PostgreSQL, Spring Security / filtro de token, formatador (Spotless etc.), perfil `prod`, Docker.

**Perfis:** `application.yaml` (comum, `profiles.default: dev`), `application-dev.yaml` (H2 na memória + console), `src/test/resources/application-test.yaml` (H2 na memória; testes com `@ActiveProfiles("test")`).

**Outros fatos do repositório**
- Repositório git criado (remote `TempJunior/fintrack-api`); primeiro commit só com `src/`. `.gitignore` exclui `data/`, `arquivos/`, `*.ofx` (exceto em `src/test/resources`) e `.env` (2026-10-20).
- Documentos do projeto em `project/` (SRS, arquitetura, modelo de dados, estado, guia Java) e instruções em `context/`.
- `pom.xml` com metadados vazios (`name`, `description`, `scm` etc. gerados pelo Initializr).

### Mapa do código (`io.github.tempjr.fintrack`)
| Pacote | Conteúdo |
|---|---|
| `FintrackApplication` | Main do Spring Boot |
| `model` | Entidades JPA: `Conta`, `Transacao`, `Categoria`, `RegraCategorizacao`, `Importacao`; `DedupKey` (cálculo da chave de dedup) |
| `model.enums` | `Instituicao` (com código do banco), `TipoConta`, `TipoTransacao`, `OrigemCategoria`, `Operador`, `StatementFormat`, `ResultadoImportacao` |
| `service` | `ContaService`/`Impl` (criar, listar, buscar, renomear, inativar), `TransacaoService`/`Impl` (`salvarNovas` com dedup) + `ResultadoDedup`, `ImportService`/`Impl` (protótipo: lê OFX do disco e delega ao `TransacaoService`) |
| `repository` | `ContaRepository` (listagens ordenadas), `TransacaoRepository` (`findDedupKeysExistentes`, `findByContaId` paginado) |
| `controller` | `ContaController` (`/api/v1/contas`) |
| `dto` | `request/CriarContaRequest`, `request/AtualizarContaRequest`, `response/ContaResponse` |
| `config` | `OpenApiConfig` |
| `exception` | `RecursoNaoEncontradoException`, `GlobalExceptionHandler` (ProblemDetail) |
| `src/test` | Unitários sem Spring: `ContaTest`, `TransacaoTest`, `DedupKeyTest`, `ContaServiceImplTest`, `TransacaoServiceImplTest`. Web: `ContaControllerTest` (`@WebMvcTest`). Integração: `TransacaoRepositoryTest`, `TransacaoServiceIntegrationTest` (`@DataJpaTest` + Flyway), `OpenApiContractTest`. `FintrackApplicationTests` |
| `resources/db/migration` | `V1__schema_inicial.sql` (6 tabelas, constraints e índices do modelo) |
| `arquivos/itau.ofx` | Extrato real Itaú (16 transações, jun–jul/2026) — fora do git |

---

## 4. Progresso

### Backend (Spring Boot)
| Área | Status | Observações |
|---|---|---|
| Esqueleto do projeto (build, formatador, perfis) | 🟨 | Maven + Boot 3.5.16 + Java 21 + git; pacotes por camada; Flyway + `validate`; perfis `dev`/`test`. Falta formatador, perfil `prod` e Postgres |
| Segurança (filtro de token) | ⬜ | |
| Contas | ✅ | Entidade, service e API completos (RF01–RF03). `numeroExterno` só será preenchido pela importação (Etapa 4) |
| Importação (detector, parser OFX, ImportService) | 🟨 | Parser OFX via ofx4j, só extrato bancário (`BANKMSGSRSV1`). Sem detector de formato, sem cartão (`CREDITCARDMSGSRSV1`), sem upload (recebe `File`) |
| Transações (dedup, enriquecimento) | 🟨 | Núcleo ✅: entidade com bruto imutável, `dedupKey`, `TransacaoService.salvarNovas` idempotente. Falta API de listagem/enriquecimento (Etapa 6) |
| Categorização (categorias, regras) | ⬜ | Entidades `Categoria` (com pai) e `RegraCategorizacao`; sem lógica de regra |
| Relatórios | ⬜ | |
| OpenAPI / Swagger | 🟨 | springdoc configurado; cobre contas. Cresce a cada endpoint novo |

### Infraestrutura
| Item | Status | Observações |
|---|---|---|
| docker compose local (api + postgres) | ⏸ | Adiado: sem Docker na máquina. Hoje H2 na memória |
| Instância EC2 + Elastic IP + security group | ⬜ | |
| Domínio + Caddy com HTTPS (D-07) | ⬜ | |
| Backup automatizado | ⬜ | |

### App Android
| Feature | Status | Observações |
|---|---|---|
| Esqueleto (Compose, Hilt, Retrofit, tema) | ⬜ | Nenhum código Android no repositório |
| `settings` (servidor + token) | ⬜ | |
| `accounts` | ⬜ | |
| `importing` | ⬜ | |
| `transactions` | ⬜ | |
| `categorization` | ⬜ | |
| `reports` (D-08) | ⬜ | |

---

## 5. Bancos testados com arquivos reais
| Banco | Tipo de conta | Versão OFX | Resultado |
|---|---|---|---|
| Itaú | Corrente (`CHECKING`, BANKID 0341) | 1.02 (SGML, CHARSET 1252) | 🟨 Importação conferida em 2026-10-20 (16 novas; reimportação 0). Falta teste automatizado com arquivo anonimizado em `src/test/resources` (Etapa 4) |
| Nubank | | | Não testado. Atenção: FITID do Nubank não é numérico (ver §7, item 1) |

## 6. Decisões tomadas durante a implementação
Decisões pequenas que ainda não viraram ADR. Se uma delas crescer, promova para ADR na arquitetura.

- 2026-07 — Parser OFX com a biblioteca `ofx4j` 1.39 — evita escrever parser SGML/XML próprio.
- 2026-07 — H2 em arquivo (`./data/appdb`) para desenvolvimento local — rapidez no protótipo; Postgres fica para o compose. *(Substituído em 2026-10-20 por H2 na memória; a pasta `data/` não é mais usada.)*
- 2026-07 — `Conta.id` = BANKID do OFX (ex.: Itaú 0341) — vincular o arquivo à conta sem seleção manual. **Revisar** (ver §7).
- 2026-07 — `Transacao.id` = FITID do OFX, usado também para deduplicação. **Revisar** (ver §7).
- 2026-10-20 — Backend em camadas MVC (controller/service/repository), sem módulos internos e com entidade JPA única — **ADR-009**. Resolve o D-06; `Money` sai do plano.
- 2026-10-20 — Dedup (Etapa 3): repetição dentro do mesmo arquivo conta como duplicada (com FITID isso não acontece nos bancos reais; com o hash do ADR-005, duas transações idênticas no mesmo dia viram uma — risco já aceito no ADR). Se duas importações simultâneas passarem pela checagem, a constraint `UNIQUE` barra e a importação inteira falha (tratamento na Etapa 4).
- 2026-10-20 — API de contas (Etapa 2): rotas em `/api/v1`; só o apelido é editável (instituição e tipo fixos, para não bagunçar transações já importadas); lista de contas sem paginação (são poucas; RNF03 é sobre transações); `numeroExterno` fora da resposta (sensível); sem "reativar" (nenhum RF pede). Regras de entidade (`IllegalArgumentException`) viram 400; erro inesperado vira 500 genérico com detalhe só no log.
- 2026-10-20 — **Postgres adiado** (sem Docker na máquina): `dev` e `test` em H2 na memória, modo PostgreSQL. As migrations usam só SQL que roda nos dois. Os dados de `dev` somem ao parar a aplicação. Risco aceito: diferenças H2 × Postgres só aparecem no 1.3, que precisa acontecer antes do deploy (Etapa 9).
- 2026-10-20 — Lombok removido do `pom.xml`; entidades com construtor e getters explícitos, sem setters em campo bruto (RNF06).
- 2026-10-20 — D-09 (número da conta), D-10 (saldo pelo LEDGERBAL), D-11 (origem da categoria) e D-12 (`Instituicao` enum) aprovados.

## 7. Problemas e dúvidas em aberto
Encontrados no mapeamento de 2026-10-20, em ordem de prioridade.

1. ~~**`Transacao.id` = `Long.valueOf(FITID)`**~~ Resolvido no 1.1 (id gerado + `fitId` texto + `dedupKey`). — FITID é string no padrão OFX (Nubank usa UUID → `NumberFormatException`) e só é único dentro da conta; dois bancos podem colidir. Além disso a entidade declara `@GeneratedValue(IDENTITY)` mas recebe id manual.
2. ~~**`Conta.id` = BANKID**~~ Resolvido no 1.1 (id gerado; BANKID em `Instituicao.getCodigo()`). — limita a uma conta por banco (corrente + cartão Itaú colidem). E no teste `0341L` é literal **octal** em Java (= 225), não 341.
3. ~~**`existsByApelidoOrInstituicao`**~~ Resolvido no 1.1 (removido). em `ContaServiceImpl` impede duas contas na mesma instituição.
4. ~~**`Transacao.categoria`**~~ Resolvido no 1.1. tem `@ManyToOne` junto com `@Enumerated` — combinação inválida; remover `@Enumerated`.
5. ~~**`listarTransacoes(conta)`**~~ Resolvido no 1.1 (`findByContaId` paginado no repository). ignora a conta e retorna `findAll()`; o `@Query` está na interface do *service*, onde não tem efeito — mover para `TransacaoRepository` (`findByConta`).
6. Valor convertido via `double` (`BigDecimal.valueOf(getAmount())`) — risco de arredondamento; avaliar ler o texto original ou fixar `setScale(2)`.
7. *(Parcial no 1.1: stream com try-with-resources. Falta o fuso — Etapa 4.)* `FileInputStream` não é fechado (usar try-with-resources); data convertida com `ZoneId.systemDefault()` em vez do fuso do arquivo.
8. *(Parcial: logger só com contagens (1.1); `RecursoNaoEncontradoException` + `GlobalExceptionHandler` (2). Falta `ArquivoInvalidoException` na Etapa 4.)* Exceções genéricas (`RuntimeException`) e `System.out.println` no lugar de logger; checagem `conta == null` é inútil (o `buscarConta` já lança).
9. ~~Testes sem asserts~~ Resolvido no 1.1/1.2 (asserts, H2 isolado na memória, `@DataJpaTest`). Original: Testes sem asserts, dependentes de estado no H2 local; criar fixture e usar banco isolado no teste.
10. ~~`ddl-auto: update`~~ Resolvido no 1.2 (Flyway + `validate`); o risco H2 × Postgres continua até o 1.3. `ddl-auto: update` + H2 pode divergir do schema real; o `appdb.trace.db` registra falhas antigas de `create table contas` (enum vazio em `instituicao`). Introduzir migrações (Flyway) junto com o Postgres.
11. Saldo do extrato (`LEDGERBAL`) é ignorado na importação — decidir se será usado para conferência.
12. ~~Onde estão `fintrack-srs.md` e `fintrack-arquitetura.md`?~~ Resolvido: estão em `project/`.
13. ~~Lacunas D-09 a D-12~~ Resolvido: aprovadas em 2026-10-20 (`fintrack-modelo-dados.md` §7).

---

## 8. Roadmap de etapas (MVP)

Cada etapa termina em algo que roda e tem teste. A ordem segue as dependências entre as partes: primeiro a base, depois contas e transações, depois o que usa os dois (importação, categorização, relatórios), por fim segurança, deploy e app.

| # | Etapa | Entrega | Requisitos / ADRs | Status |
|---|---|---|---|---|
| 0 | Repositório | git, `.gitignore` seguro, documentos no repo | RNF08, RNF23 | ✅ |
| 1 | Fundação | 1.1 ✅ pacotes por camada + entidades + testes · 1.2 ✅ Flyway `V1` + perfis + `validate` + integração em H2 · 1.3 ⏸ Postgres (compose) + Testcontainers — adiado até haver Docker; obrigatório antes da Etapa 9 | ADR-003, ADR-009, RNF19, RNF21 | 🟨 |
| 2 | Contas + padrão de API | `ContaController`/`ContaService` (criar, listar, editar, inativar); DTOs; `ProblemDetail`; springdoc-openapi | RF01–RF03, RNF22 | ✅ |
| 3 | Transações (núcleo) | `Transacao` imutável no bruto; cálculo da `dedupKey`; `TransacaoService` com dedup e constraint única | RF07, RF11, RNF04, RNF06, ADR-005 | ✅ |
| 4 | Importação | `FormatDetector`, `OfxStatementParser` (Strategy), resolver, upload multipart, validação (checksum + conta), atomicidade, auditoria (inclusive falha) | RF04–RF09, RNF05, RNF12, ADR-002, ADR-004 | ⬜ |
| 5 | Categorização | Categorias/subcategorias, regras, `CategorizacaoService`, aplicação na importação, reaplicar em lote | RF15–RF20 | ⬜ |
| 6 | Transações (API) | Listagem paginada com filtros, enriquecimento, transferência interna | RF12–RF14, RF18, RNF03 | ⬜ |
| 7 | Relatórios | Receitas × despesas, por categoria, evolução mensal, comparativo, saldos | RF21–RF26 | ⬜ |
| 8 | Segurança | Filtro de token, health check liberado, Swagger fechado em prod | RNF11, ADR-008 | ⬜ |
| 9 | Infra / deploy | Dockerfile da API, compose completo com Caddy, EC2 + Elastic IP + security group, domínio (D-07), backup para S3 | RNF07, RNF10, RNF19–RNF21, ADR-007 | ⬜ |
| 10 | App Android | Esqueleto → settings → accounts → importing → transactions → categorization → reports (D-08) | RF28–RF30, RNF25, RNF26, ADR-006 | ⬜ |

Formatador no build (RNF18) entra na Etapa 1 ou 2, assim que o código for reorganizado.
