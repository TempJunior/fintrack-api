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
**Etapa 8 — Segurança** (✅ 2026-10-20, branch `feat/etapa-8-seguranca`, aguardando commit).
- Token único em `Authorization: Bearer` validado por filtro do Spring Security, comparação em tempo constante (RNF11, ADR-008). Sem token/errado → 401 em `ProblemDetail`.
- Token de `FINTRACK_TOKEN` (≥ 32 caracteres), validado na subida (RNF09). Em `dev` há um token local padrão; em `test`, um fixo.
- `/actuator/health` aberto e sem detalhes (RNF14); demais endpoints do actuator não expostos.
- Perfil `prod`: banco por variáveis de ambiente, sem Swagger nem console H2, atrás de proxy. **Só sobe depois do 1.3** (driver do Postgres).
- 174 testes passando.

**Backend do MVP completo em funcionalidade e segurança.** Faltam: Postgres + docker compose (1.3), deploy (Etapa 9) e o app (Etapa 10).

## 2. Próximo passo
Duas frentes independentes; a ordem depende de ter Docker:
- **Com Docker:** 1.3 (Postgres no compose + driver + Testcontainers) e depois **Etapa 9 — Infra/deploy** (Dockerfile, compose com Caddy, EC2, domínio D-07, backup).
- **Sem Docker:** **Etapa 10 — App Android**, contra o backend local (`http://10.0.2.2:8080` no emulador, token de dev).

---

## 3. Stack e dependências (mapeado em 2026-10-20)

| Item | Versão / valor | Observação |
|---|---|---|
| Java | 21 (`java.version` no pom) | JDK instalado na máquina: 25.0.1 |
| Spring Boot (parent) | 3.5.16 | |
| Build | Maven Wrapper 3.3.4 → Maven 3.9.16 | `mvnw` / `mvnw.cmd` |
| `spring-boot-starter-web` | (BOM) | `ContaController` |
| `spring-boot-starter-validation` | (BOM) | Bean Validation nos DTOs |
| `spring-boot-starter-security` | (BOM) | Spring Security 6.5; filtro de token (ADR-008) |
| `spring-boot-starter-actuator` | (BOM) | Só `health` exposto, sem detalhes |
| `spring-security-test` | (BOM), test | |
| `springdoc-openapi-starter-webmvc-ui` | 2.8.17 | Spec `/v3/api-docs`, UI `/swagger-ui.html` (fechar em prod na Etapa 8) |
| `spring-boot-starter-data-jpa` | (BOM) | Hibernate 6, `ddl-auto: validate`, `open-in-view: false` |
| `ofx4j` (`com.webcohesion.ofx4j`) | 1.39 | Parser OFX (SGML 1.x e XML 2.x) |
| `h2` | (BOM), runtime | Na memória, modo PostgreSQL, em `dev` e `test`. Console em `/h2-console` só no `dev` |
| `flyway-core` | (BOM) | Migrations em `src/main/resources/db/migration`; `V1__schema_inicial.sql` |
| `spring-boot-starter-test` | (BOM), test | JUnit 5 |

**Removido:** Lombok (2026-10-20, incremento 1.1 — não adotado, ver instruções).

**Ainda não presentes** (previstos no plano): driver PostgreSQL (+ `flyway-database-postgresql`), formatador (Spotless etc. — RNF18), Docker.

**Perfis:** `application.yaml` (comum, `profiles.default: dev`, token de `FINTRACK_TOKEN`, actuator só health), `application-dev.yaml` (H2 na memória + console + token local), `application-prod.yaml` (banco e token do ambiente, sem docs/console), `src/test/resources/application-test.yaml` (H2 na memória, token fixo, estatísticas do Hibernate; testes com `@ActiveProfiles("test")`).

**Outros fatos do repositório**
- Repositório git criado (remote `TempJunior/fintrack-api`); primeiro commit só com `src/`. `.gitignore` exclui `data/`, `arquivos/`, `*.ofx` (exceto em `src/test/resources`) e `.env` (2026-10-20).
- Documentos do projeto em `project/` (SRS, arquitetura, modelo de dados, estado, guia Java) e instruções em `context/`.
- `pom.xml` com metadados vazios (`name`, `description`, `scm` etc. gerados pelo Initializr).

### Mapa do código (`io.github.tempjr.fintrack`)
| Pacote | Conteúdo |
|---|---|
| `FintrackApplication` | Main do Spring Boot |
| `model` | Entidades JPA: `Conta`, `Transacao`, `Categoria` (2 níveis, renomear), `RegraCategorizacao` (`corresponde`), `Importacao`; `DedupKey`, `CategorizadorPorRegras` (Java puro) |
| `model.enums` | `Instituicao` (com código do banco), `TipoConta`, `TipoTransacao`, `OrigemCategoria`, `Operador`, `StatementFormat`, `ResultadoImportacao` |
| `service` | `ContaService`/`Impl` (criar, listar, buscar, renomear, inativar), `TransacaoService`/`Impl` (`salvarNovas` com dedup, `reaplicarRegras`, `listar` com filtros, `buscar`, categoria manual, `enriquecer`) + `ResultadoDedup`, `ResultadoReaplicacao`, `CategorizacaoService`/`Impl` (categorias, regras, `aplicarRegras`), `RelatorioService`/`Impl` (resumo, por categoria, mensal, comparativo, saldos) + records em `service.relatorio`, `ImportService`/`Impl` (Fluxo B completo com `TransactionTemplate`) |
| `parser` | `FormatDetector`, `StatementParser`, `OfxStatementParser`, `StatementParserResolver`, `OfxCharset`, `OfxDataLiteralConversion`, `ImportContext`, `ParsedStatement`, `AccountRef`, `RawTransaction` |
| `repository` | `ContaRepository`, `TransacaoRepository` (`findDedupKeysExistentes`, `findByContaId`, `findCategorizaveis` em Slice, `findAll(Specification)` com `@EntityGraph`, `findDetalhadaById`) + `TransacaoFiltro` e `TransacaoSpecs`, `ImportacaoRepository` (`findComSaldo`), `RelatorioRepository` (classe com `EntityManager`: totais, por categoria, por mês) + `FiltroRelatorio`, `CategoriaRepository` (`existeNomeNoNivel`), `RegraCategorizacaoRepository` (`findAllComCategoria` com JOIN FETCH) |
| `controller` | `ContaController`, `ImportController`, `CategoriaController` (`/api/v1/categorias`), `RegraController` (`/api/v1/regras`), `TransacaoController` (`/api/v1/transacoes`), `RelatorioController` (`/api/v1/relatorios`) |
| `dto` | Requests: `CriarContaRequest`, `AtualizarContaRequest`, `CriarCategoriaRequest`, `RenomearCategoriaRequest`, `CriarRegraRequest`. Responses: `ContaResponse`, `ImportacaoResponse` (dinheiro como string), `CategoriaResponse` (árvore), `RegraResponse`, `TransacaoResponse`, `PaginaResponse<T>`, `RelatorioResponses` (records aninhados dos relatórios). Requests da Etapa 6: `CategorizarTransacaoRequest`, `EnriquecerTransacaoRequest` |
| `config` | `OpenApiConfig` (com esquema bearer para o botão Authorize do Swagger) |
| `security` | `SecurityConfig`, `TokenAuthenticationFilter`, `ProblemDetailAuthenticationEntryPoint`, `SegurancaProperties` |
| `exception` | `RecursoNaoEncontradoException`, `ImportacaoRejeitadaException`, `ArquivoInvalidoException`, `ConflitoException`, `GlobalExceptionHandler` (ProblemDetail: 400, 404, 409, 413, 422, 500) |
| `src/test` | Unitários sem Spring: `ContaTest`, `TransacaoTest`, `DedupKeyTest`, `RegraCategorizacaoTest`, `ContaServiceImplTest`, `TransacaoServiceImplTest`, `CategorizacaoServiceImplTest`, `RelatorioServiceImplTest`. Parser: `OfxStatementParserTest`, `FormatDetectorTest`, `OfxCharsetTest`. Web: `ContaControllerTest`, `ImportControllerTest` (`@WebMvcTest`). Integração: `TransacaoRepositoryTest`, `TransacaoServiceIntegrationTest` (`@DataJpaTest` + Flyway), `ImportServiceIntegrationTest`, `CategorizacaoApiIntegrationTest`, `TransacaoApiIntegrationTest` e `RelatorioApiIntegrationTest` (`@SpringBootTest`, com `TokenDeTesteConfig` pondo o token em toda requisição, sem transação de teste, limpam o banco antes e depois; o último conta consultas SQL via estatísticas do Hibernate), `OpenApiContractTest`. Segurança: `SegurancaIntegrationTest`, `SegurancaPropertiesTest`, `PerfilProdTest` (prod com datasource trocado por H2). `FintrackApplicationTests` |
| `resources/db/migration` | `V1__schema_inicial.sql` (6 tabelas, constraints e índices do modelo) |
| `test/resources/ofx` | OFX **sintéticos**: Itaú v1 em Windows-1252 (com `–` para pegar erro de encoding), cartão v2 UTF-8 com FITID UUID e datas sem fuso, corrompido, PDF e CSV. `*.ofx` marcado como `binary` no `.gitattributes` |
| `arquivos/itau.ofx` | Extrato real Itaú (16 transações, jun–jul/2026) — fora do git |

---

## 4. Progresso

### Backend (Spring Boot)
| Área | Status | Observações |
|---|---|---|
| Esqueleto do projeto (build, formatador, perfis) | 🟨 | Maven + Boot 3.5.16 + Java 21 + git; pacotes por camada; Flyway + `validate`; perfis `dev`/`test`. Falta formatador, perfil `prod` e Postgres |
| Segurança (filtro de token) | ✅ | Token Bearer, 401 em ProblemDetail, health aberto, perfil `prod` (ADR-008) |
| Contas | ✅ | Entidade, service e API completos (RF01–RF03). `numeroExterno` só será preenchido pela importação (Etapa 4) |
| Importação (detector, parser OFX, ImportService) | ✅ | Conta e cartão, v1 e v2; upload; validação da conta; atomicidade; auditoria; categorização automática |
| Transações (dedup, enriquecimento) | ✅ | Núcleo + API: listagem paginada com filtros, detalhe, categoria manual, nota, tags, transferência interna |
| Categorização (categorias, regras) | ✅ | Categorias (2 níveis), regras, aplicação na importação, reaplicação em lote (RF15–RF20) |
| Relatórios | ✅ | Resumo, por categoria, mensal, comparativo, saldos (RF21–RF26) |
| OpenAPI / Swagger | ✅ | Cobre toda a API do MVP, com esquema de token; desligado no perfil `prod` |

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
| Itaú | Corrente (`CHECKING`, BANKID 0341) | 1.02 (SGML, CHARSET 1252) | ✅ Pela API em 2026-10-20: 16 novas; reimportação 0; conta errada → 422. Teste automatizado com arquivo sintético equivalente. O arquivo real não tem acentos (só ASCII) |
| Nubank | | | Não testado com arquivo real. Coberto por arquivo sintético (cartão v2, FITID UUID). **Conferir com arquivo real:** versão do OFX, BANKID (código assumido `0260`) e formato das datas |

## 6. Decisões tomadas durante a implementação
Decisões pequenas que ainda não viraram ADR. Se uma delas crescer, promova para ADR na arquitetura.

- 2026-07 — Parser OFX com a biblioteca `ofx4j` 1.39 — evita escrever parser SGML/XML próprio.
- 2026-07 — H2 em arquivo (`./data/appdb`) para desenvolvimento local — rapidez no protótipo; Postgres fica para o compose. *(Substituído em 2026-10-20 por H2 na memória; a pasta `data/` não é mais usada.)*
- 2026-07 — `Conta.id` = BANKID do OFX (ex.: Itaú 0341) — vincular o arquivo à conta sem seleção manual. **Revisar** (ver §7).
- 2026-07 — `Transacao.id` = FITID do OFX, usado também para deduplicação. **Revisar** (ver §7).
- 2026-10-20 — Backend em camadas MVC (controller/service/repository), sem módulos internos e com entidade JPA única — **ADR-009**. Resolve o D-06; `Money` sai do plano.
- 2026-10-20 — Segurança (Etapa 8):
  - Filtro fora de `@Component` (senão o Boot o registraria também como filtro de servlet e ele rodaria duas vezes).
  - Comparação por SHA-256 + `MessageDigest.isEqual` (tempo constante, sem revelar o tamanho do token).
  - Sem sessão e sem CSRF (sem cookies; cliente nativo). Sem usuário padrão do Boot (`UserDetailsService` vazio).
  - Token de dev fixo no `application-dev.yaml`: não é segredo, só vale localmente; prod exige `FINTRACK_TOKEN`.
  - `PathPatternRequestMatcher` (Security 6.5) para as rotas e `PathRequest.toH2Console()` para o console, evitando a ambiguidade de matchers com mais de um servlet.
  - **Correção de um erro meu na Etapa 6:** a configuração `generate_statistics` nunca tinha sido gravada, então o teste de N+1 contava zero consultas e passava sem medir nada. Corrigido: estatísticas ligadas e o teste exige que elas estejam ativas e que a contagem fique entre 1 e 3. Passa de verdade.
- 2026-10-20 — Relatórios (Etapa 7):
  - Receita = valor positivo; despesa = valor negativo, exibida como positiva; saldo = receitas − despesas. Transferência interna fora de tudo (RF14).
  - Por categoria: agrupado pela raiz (subcategorias somadas e detalhadas dentro), mais despesa primeiro, "Não categorizado" por último. Mostra despesas **e** receitas por categoria (estorno do cartão cai na mesma categoria da compra).
  - Mensal: `mesInicio`/`mesFim` (yyyy-MM), máx. 36 meses, inclui meses sem movimento (zero). Comparativo: 2 a 12 meses distintos; 2 consultas por mês (simples; trocar por consulta única se pesar).
  - Saldos: só contas ativas; importação mais recente pela **data do saldo** (importar extrato antigo depois não volta o saldo no tempo); conta sem importação aparece sem saldo e fica fora do consolidado.
  - Agregações num `RelatorioRepository` (classe com `EntityManager`), JPQL montado só com os filtros preenchidos e parâmetros nomeados. Evita `(:param is null or ...)`, que costuma dar erro de tipo no PostgreSQL.
- 2026-10-20 — Transações (Etapa 6):
  - Categoria manual em endpoint próprio (`PUT/DELETE /{id}/categoria`): no PATCH não daria para distinguir "não mexer" de "remover" (os dois chegam nulos).
  - PATCH: campo ausente não muda; `tags` substitui a lista inteira; `nota` vazia apaga. Campos do dado bruto enviados pelo cliente são ignorados (o DTO não os tem).
  - Filtro por categoria inclui as subcategorias. Busca por texto ignora maiúsculas, mas **não** acentos (exigiria `unaccent` no Postgres) — possível melhoria.
  - Ordem fixa: data desc, id desc. Tamanho de página: padrão 20, máximo 100.
  - Resposta paginada em `PaginaResponse` própria, em vez de serializar o `Page` do Spring Data (JSON instável).
  - N+1: `@EntityGraph` para conta e categoria (ToOne, seguro com paginação); tags por `hibernate.default_batch_fetch_size: 50` (JOIN com coleção quebraria a paginação no banco).
- 2026-10-20 — Categorização (Etapa 5):
  - CONTAINS e STARTS_WITH ignoram maiúsculas e acentos (bancos escrevem em caixa alta e às vezes sem acento); REGEX ignora maiúsculas e é validada na criação (400 se inválida).
  - Ordem de avaliação: menor prioridade primeiro; empate → regra criada antes. A primeira que casa vence.
  - Categorias em no máximo 2 níveis (exemplo do RF15). Nome único por nível sem diferenciar maiúsculas (409); a constraint do banco é a rede de segurança.
  - Categorias não são excluídas (só renomeadas): nenhum RF pede, e excluir categoria em uso exigiria decidir o destino das transações. Regras podem ser excluídas.
  - Na importação, as regras rodam antes de gravar (INSERT já com categoria, sem UPDATE depois).
  - Reaplicar (RF19): recalcula transações sem categoria ou com origem REGRA; as MANUAL nem entram; se nenhuma regra casa mais, volta a "Não categorizado". Em fatias de 500 (`Slice`, sem count).
  - Endpoint de reaplicação fica em `/api/v1/regras/reaplicar`, mas quem altera as transações é o `TransacaoService` (dono do repository).
- 2026-10-20 — Importação (Etapa 4):
  - **D-13:** checksum da §7.4 não se aplica ao OFX (o arquivo só traz o saldo final). Validação OFX = tipo (conta × cartão) + banco (`BANKID`, sem zeros à esquerda) + número (`ACCTID`, D-09).
  - **Encoding:** descoberto que a ofx4j lê tudo como ISO-8859-1; `OfxCharset` aplica o encoding do arquivo (ADR-004 corrigido).
  - **Datas:** a ofx4j assume UTC em data sem fuso; `OfxDataLiteralConversion` usa só a data literal.
  - **Transações do banco:** `TransactionTemplate` no `ImportService` para registrar a falha depois do rollback.
  - Um extrato (uma conta) por arquivo; arquivo com várias contas é rejeitado.
  - Upload até 5 MB (`spring.servlet.multipart`); acima disso, 413 sem registro no histórico.
  - Nome do arquivo não vai para o log (pode conter o número da conta).
  - Testes de integração que fazem commit limpam o banco antes e depois; testes não usam `count()` global.
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
6. ~~Valor convertido via `double`~~ Resolvido na Etapa 4 (`getBigDecimalAmount`). Original: Valor convertido via `double` (`BigDecimal.valueOf(getAmount())`) — risco de arredondamento; avaliar ler o texto original ou fixar `setScale(2)`.
7. ~~Fuso e stream~~ Resolvido na Etapa 4 (data literal via `OfxDataLiteralConversion`; leitura em memória). Original: `FileInputStream` não é fechado (usar try-with-resources); data convertida com `ZoneId.systemDefault()` em vez do fuso do arquivo.
8. ~~Exceções genéricas~~ Resolvido (Etapas 1, 2 e 4). Exceções genéricas (`RuntimeException`) e `System.out.println` no lugar de logger; checagem `conta == null` é inútil (o `buscarConta` já lança).
9. ~~Testes sem asserts~~ Resolvido no 1.1/1.2 (asserts, H2 isolado na memória, `@DataJpaTest`). Original: Testes sem asserts, dependentes de estado no H2 local; criar fixture e usar banco isolado no teste.
10. ~~`ddl-auto: update`~~ Resolvido no 1.2 (Flyway + `validate`); o risco H2 × Postgres continua até o 1.3. `ddl-auto: update` + H2 pode divergir do schema real; o `appdb.trace.db` registra falhas antigas de `create table contas` (enum vazio em `instituicao`). Introduzir migrações (Flyway) junto com o Postgres.
11. ~~`LEDGERBAL` ignorado~~ Resolvido na Etapa 4: gravado em `importacoes.saldo_declarado` (D-10); checksum não se aplica ao OFX (D-13). Original: Saldo do extrato (`LEDGERBAL`) é ignorado na importação — decidir se será usado para conferência.
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
| 4 | Importação | ✅ `FormatDetector`, `OfxStatementParser` (Strategy), resolver, upload multipart, validação (checksum + conta), atomicidade, auditoria (inclusive falha) | RF04–RF09, RNF05, RNF12, ADR-002, ADR-004 | ✅ |
| 5 | Categorização | ✅ Categorias/subcategorias, regras, `CategorizacaoService`, aplicação na importação, reaplicar em lote | RF15–RF20 | ✅ |
| 6 | Transações (API) | ✅ Listagem paginada com filtros, enriquecimento, transferência interna | RF12–RF14, RF18, RNF03 | ✅ |
| 7 | Relatórios | ✅ Receitas × despesas, por categoria, evolução mensal, comparativo, saldos | RF21–RF26 | ✅ |
| 8 | Segurança | ✅ Filtro de token, health check liberado, Swagger fechado em prod | RNF11, ADR-008 | ✅ |
| 9 | Infra / deploy | Dockerfile da API, compose completo com Caddy, EC2 + Elastic IP + security group, domínio (D-07), backup para S3 | RNF07, RNF10, RNF19–RNF21, ADR-007 | ⬜ |
| 10 | App Android | Esqueleto → settings → accounts → importing → transactions → categorization → reports (D-08) | RF28–RF30, RNF25, RNF26, ADR-006 | ⬜ |

Formatador no build (RNF18) entra na Etapa 1 ou 2, assim que o código for reorganizado.
