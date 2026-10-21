# Fintrack — Documento de Arquitetura

| Campo | Valor |
|---|---|
| Projeto | Fintrack |
| Versão do documento | 0.5 |
| Documento relacionado | fintrack-srs.md (requisitos, v0.2) · fintrack-modelo-dados.md (diagramas) |
| Status | Backend organizado em camadas MVC (ADR-009) — sujeita a refino durante implementação |
| Última atualização | 2026-10-20 |

> Este documento assume os requisitos do SRS v0.2: sistema single-user com credencial única, MVP = Módulos 1–6 com importação **apenas OFX**, monólito (ADR-001) organizado em camadas MVC (ADR-009), cliente Android nativo (ADR-006) e backend em AWS EC2 (ADR-007).

---

## 1. AS-IS → TO-BE

### 1.1 AS-IS (situação atual, sem o sistema)
Hoje o controle financeiro é manual e fragmentado:
- O usuário acessa cada banco (Itaú, Nubank, Mercado Pago) separadamente.
- Cada banco exporta em um formato diferente (OFX, CSV) e com convenções diferentes.
- Não há consolidação: para saber "quanto gastei com X no mês", é preciso olhar conta por conta.
- Apps de mercado (Mobills, Organizze) não integram bem com esses bancos, o que gera retrabalho manual.
- Não há categorização automática nem histórico unificado.

### 1.2 TO-BE (estado desejado com o Fintrack)
O Fintrack é um ponto único de consolidação:
- O usuário exporta o extrato de cada banco (ainda manualmente) e faz upload pelo app Android.
- O sistema **normaliza** os arquivos OFX (v1 e v2) em um modelo único (canônico), desenhado para receber outros formatos no futuro (ex.: CSV do Mercado Pago — Apêndice A).
- O sistema **deduplica** transações automaticamente, permitindo reimportar sem medo.
- O sistema **categoriza** transações por regras configuráveis.
- O usuário enxerga tudo consolidado no app: saldo por conta, gasto por categoria, evolução mensal.
- O dado bruto do banco é preservado imutável; enriquecimentos ficam à parte.

### 1.3 O que o TO-BE deliberadamente NÃO resolve no MVP
- Conexão automática com bancos (exigiria Open Finance / certificação regulatória — fora de escopo). A entrada continua sendo upload manual.
- Multiusuário (Fase 2). A autenticação de usuário único **entra** no MVP por causa da exposição pública (ADR-008).
- Importação de CSV (Mercado Pago) e modo offline no app (Evolução Futura).
- Orçamentos/metas, notificações, detecção de recorrências (Evolução Futura).

### 1.4 Visão de Fase 2 (registrada, fora do MVP)
- Extrair a importação para um serviço separado (exercício de microserviços — ver ADR-001). Com as camadas MVC (ADR-009), isso exige antes separar as classes de importação das demais.
- Evoluir a autenticação de token único para login com usuários reais, se o sistema virar multiusuário.
- Orçamento por categoria e alertas.

---

## 2. Fluxos completos desejados (TO-BE)

Quatro fluxos cobrem todo o uso do sistema.

### Fluxo A — Configuração inicial (uma vez, ou ao adicionar banco novo)
1. Usuário cadastra uma conta: instituição, tipo, apelido, moeda (RF01).
2. Conta fica disponível para receber importações.

### Fluxo B — Importação recorrente (o fluxo central — ver diagrama TO-BE)
1. Usuário exporta o extrato OFX no banco (Itaú, Nubank).
2. No app Android, seleciona o arquivo no dispositivo e faz upload **associado a uma conta** (RF04, RF29). A instituição passa a ser conhecida.
3. Sistema **detecta o formato** (magic bytes / header) e **resolve o parser** por (instituição, formato).
4. Parser lê o arquivo e produz o **modelo canônico** (RF06).
5. Sistema **valida**: checksum (soma das transações vs. saldo declarado) e conferência da conta declarada no arquivo (RF05).
6. Sistema **deduplica** (chave FITID + conta) e **persiste** as novas, preservando o bruto imutável (RF07, RF11).
7. Sistema **aplica regras de categorização** nas novas transações (RF17).
8. Sistema registra um **resumo de importação** (lidas, novas, duplicadas, erros) na auditoria (RF08, RNF12).
9. Transações ficam disponíveis para relatórios.

### Fluxo C — Revisão e enriquecimento
1. Usuário revisa transações "Não categorizado" (RF20).
2. Ajusta categoria manualmente (RF18), adiciona nota/tag (RF13).
3. Marca pares/transações como **transferência interna** (RF14).
4. Cria novas regras de categorização (RF16) e reaplica em lote no histórico (RF19).

### Fluxo D — Consulta e relatórios
1. Usuário filtra por período, conta, categoria, tipo, texto (RF12, RF26).
2. Visualiza dashboards: receitas vs. despesas, gasto por categoria, evolução mensal, comparativo, saldos (RF21–RF25).

---

## 3. Arquitetura de alto nível

### 3.1 Estilo
Monólito (ADR-001): **um único deployable** Spring Boot, organizado em **camadas MVC** (ADR-009) — controller, service, repository — com entidades JPA como modelo. Não há divisão interna em módulos.

### 3.2 Camadas e responsabilidades

| Camada / pacote | Responsabilidade | Chama |
|---|---|---|
| `controller` | Endpoints REST: recebe e valida DTOs, devolve DTOs e status HTTP. Sem regra de negócio. | `service` |
| `service` | Regras de negócio, orquestração e transações (`@Transactional`). | `repository`, outros services, `parser` |
| `repository` | Acesso a dados (Spring Data JPA). | — |
| `model` | Entidades JPA e enums. Regras simples da própria entidade ficam nela. | — |
| `dto` | Objetos de entrada e saída da API (`record` + Bean Validation). | — |
| `parser` | Detecção de formato e parsers de extrato (Strategy — ADR-002). | — |
| `exception` | Exceções de negócio e conversão para `ProblemDetail`. | — |
| `security` / `config` | Filtro de token (ADR-008) e configurações. Sem regra de negócio. | — |

### 3.3 Regras entre camadas
- Dependências só descem: `controller` → `service` → `repository`. Nunca o contrário.
- Controller não acessa repository e nunca devolve entidade (só DTO).
- Cada repository tem um service "dono"; os outros services passam por ele. Exceção: `RelatorioService` (só leitura) consulta repositories direto para agregações.
- Sem ciclos entre services.

Diagramas das entidades, das camadas e do fluxo de importação: `fintrack-modelo-dados.md`.

### 3.4 Costura para a Fase 2 (extração de microserviço)
A importação continua sendo o candidato natural a virar serviço separado. Sem módulos (ADR-009), a fronteira não é garantida pela estrutura de pacotes; para ajudar a extração futura, o `ImportService` fala com o resto **só por services** (`ContaService`, `TransacaoService`, `CategorizacaoService`), nunca por repositories de outras entidades.

### 3.5 Testabilidade (RNF17)
Sem a separação domínio × JPA, o RNF17 é atendido assim: entidades são testadas como objetos Java comuns; services são testados com JUnit 5 + Mockito, mockando os repositories, sem subir Spring nem banco. Testes de integração cobrem repositories e a importação ponta a ponta.

### 3.6 Visão de sistema (cliente, borda, backend)

```
[App Android]  --HTTPS + token-->  [Caddy]  -->  [Spring Boot]  -->  [PostgreSQL]
(Kotlin/Compose)                  (TLS, proxy)   (monólito)
                                  └──────── EC2 + docker compose ────────┘
```

O app é **só cliente**: não tem regra de negócio nem banco local. Dedup, categorização e cálculo de relatórios acontecem no backend, que é a única fonte da verdade (ADR-006). Detalhes do app na §8; da implantação na §9.

---

## 4. Modelo canônico (contrato interno de importação)

Toda origem converge para este modelo. Ele é a saída dos parsers e a linguagem que o resto do sistema entende. No MVP a única origem é **OFX (v1 e v2)**, analisada a partir do arquivo real do Itaú. O modelo é propositalmente neutro de formato, para receber o CSV do Mercado Pago no futuro sem alterações (mapeamento preservado no Apêndice A).

### 4.1 `ParsedStatement` (resultado do parse de um arquivo)
| Campo | Tipo | Origem OFX |
|---|---|---|
| `declaredAccount` | `AccountRef` (banco, conta) | `BANKACCTFROM` (BANKID, ACCTID); cartão: `CCACCTFROM` |
| `period` | início/fim | `DTSTART`, `DTEND` |
| `ledgerBalance` | `BigDecimal` | `LEDGERBAL/BALAMT` |
| `summary` | resumo p/ checksum | soma das transações |
| `transactions` | `List<RawTransaction>` | lista de `STMTTRN` |

### 4.2 `RawTransaction` (uma transação, normalizada)
| Campo canônico | Tipo | OFX (`STMTTRN`) |
|---|---|---|
| `providerTransactionId` | `String` (opcional) | `<FITID>` |
| `date` | `LocalDate` | `<DTPOSTED>` (só a data) |
| `amount` | `BigDecimal` | `<TRNAMT>` |
| `description` | `String` | `<MEMO>` |

Regras de derivação (valem para qualquer formato, atual ou futuro — é isso que mantém o modelo coerente):
- **Tipo (débito/crédito)** deriva do **sinal de `amount`** (negativo = débito). O `TRNTYPE` do OFX é apenas reforço redundante.
- **Valor** é sempre `BigDecimal`, nunca `double` (ADR-003).
- **Hora é ignorada**: o Itaú carimba `100000` fixo; só a data tem significado.

---

## 5. Modelo de dados (entidades persistidas)

Distinção central (RF11/RNF06): campos **brutos** são imutáveis após a importação; campos de **enriquecimento** são mutáveis.

### 5.1 Entidades

**Instituicao** (enum — D-12)
- `ITAU` (`0341`), `NUBANK` (`0260`), `MERCADOPAGO` (`0323`); cada valor carrega o `codigo` do banco.

**Conta**
- `id`, `instituicao`, `tipo` (CORRENTE | POUPANCA | CARTAO), `apelido`, `moeda` (BRL), `numeroExterno` (ACCTID do OFX, nullable — D-09), `ativa` (boolean).

**Transacao**
- Brutos (imutáveis): `id`, `conta_id`, `providerTransactionId` (nullable), `dataPostada`, `valor` (BigDecimal), `descricaoOriginal`.
- Derivado (não persistido): `tipo` (CREDITO | DEBITO — do sinal do valor).
- Enriquecimento (mutável): `categoria_id` (nullable), `origemCategoria` (MANUAL | REGRA — D-11), `nota` (nullable), `transferenciaInterna` (boolean, default false), `tags`.
- Dedup: `dedup_key` (ver 5.2).

**Categoria**
- `id`, `nome`, `categoria_pai_id` (nullable, auto-referência para subcategoria).

**RegraCategorizacao**
- `id`, `categoria_id`, `operador` (CONTAINS | STARTS_WITH | REGEX), `padrao`, `prioridade` (ordem de avaliação).

**Importacao** (auditoria)
- `id`, `conta_id`, `nomeArquivo`, `formato`, `dataHora`, `totalLido`, `novas`, `duplicadas`, `erros`, `resultado`, `saldoDeclarado` e `dataSaldo` (LEDGERBAL — D-10).

### 5.2 Chave de deduplicação
- `dedup_key` = `providerTransactionId` quando presente; senão, um hash de `conta + data + valor + descrição normalizada` (fallback defensivo — ADR-005).
- **Constraint única**: `UNIQUE (conta_id, dedup_key)`. É a rede de segurança no banco, além da checagem em aplicação (belt and suspenders → garante RNF04).
- Observação: os bancos reais do MVP (Itaú e Nubank) têm FITID estável, então o fallback por hash não é exercido por eles; existe apenas para robustez futura.

---

## 6. Estrutura de pacotes (Spring)

Organização **por camada** (ADR-009). Pacote base: `io.github.tempjr.fintrack`.

```
io.github.tempjr.fintrack
├── FintrackApplication
├── config/                    (OpenAPI, configurações gerais)
├── security/                  (filtro de token, config do Spring Security — ADR-008)
├── controller/                (ContaController, ImportController, TransacaoController,
│                               CategoriaController, RegraController,
│                               RelatorioController)
├── service/                   (ContaService, ImportService, TransacaoService,
│                               CategorizacaoService, RelatorioService
│                               + implementações, se mantido o padrão interface + Impl)
├── repository/                (ContaRepository, TransacaoRepository, CategoriaRepository,
│                               RegraCategorizacaoRepository, ImportacaoRepository,
│                               RelatorioRepository — agregações com EntityManager)
├── model/                     (Conta, Transacao, Categoria, RegraCategorizacao, Importacao,
│                               DedupKey, CategorizadorPorRegras)
│   └── enums/                 (Instituicao, TipoConta, TipoTransacao, OrigemCategoria,
│                               Operador, StatementFormat, ResultadoImportacao)
├── dto/
│   ├── request/
│   └── response/
├── parser/                    (StatementParser, OfxStatementParser, StatementParserResolver,
│                               FormatDetector, ImportContext, ParsedStatement, RawTransaction)
└── exception/                 (exceções de negócio, handler com ProblemDetail)
```

A entidade JPA é o modelo (sem classe de domínio separada — resolve o D-06). Diagramas em `fintrack-modelo-dados.md`.

---

## 7. Desenho interno da importação

### 7.1 Interfaces centrais (Strategy + Factory — ADR-002)

```java
// A "porta" — cada formato/banco implementa isto (Strategy)
public interface StatementParser {
    boolean supports(ImportContext ctx);
    ParsedStatement parse(InputStream in, ImportContext ctx);
}

// Contexto conhecido no momento do upload. O charset não entra aqui: no OFX ele vem
// no próprio arquivo e é lido pelo parser (ADR-004).
public record ImportContext(
    Instituicao instituicao,
    StatementFormat formato,
    String nomeArquivo) { }

public enum StatementFormat { OFX_V1, OFX_V2 }  // CSV: Evolução Futura (Apêndice A)
```

Implementações concretas (pacote `parser`):
- `OfxStatementParser` — envolve a lib ofx4j; serve qualquer banco em OFX, extrato de conta (`BANKMSGSRSV1`) e de cartão (`CREDITCARDMSGSRSV1`), um extrato por arquivo. Ajustes sobre a lib:
  - **Encoding:** a ofx4j 1.39 lê tudo como ISO-8859-1. O `OfxCharset` lê o encoding declarado no arquivo e entrega à lib um `Reader` já decodificado (ADR-004).
  - **Datas:** a ofx4j converte a data num instante e assume UTC quando não há fuso; nenhum fuso fixo acerta a volta para `LocalDate`. O `OfxDataLiteralConversion` faz a lib usar só os 8 primeiros dígitos (data literal, §4.2).
  - **Valor:** lido com `getBigDecimalAmount()`, sem passar por `double` (ADR-003).

### 7.2 Resolução (Factory / Registry)
`StatementParserResolver` recebe, via injeção do Spring, a **lista de todos** os `StatementParser` registrados e escolhe o primeiro cujo `supports(ctx)` é verdadeiro. Adicionar um banco/formato novo = criar uma nova classe `@Component` — nenhum `switch` gigante, nenhum código existente alterado (Princípio Aberto/Fechado).

### 7.3 Detecção de formato
`FormatDetector` inspeciona os primeiros bytes/linhas:
- `OFXHEADER:` no início (cabeçalho SGML; BOM e espaços ignorados) → OFX_V1.
- Declaração XML + `<?OFX ...?>` → OFX_V2.
- Qualquer outra coisa (PDF, CSV, imagem) → rejeitado com mensagem clara (RF09). O app filtra o seletor de arquivos, mas o backend **não confia** nisso.

### 7.4 Orquestração (ImportService — caso de uso do Fluxo B)
1. Detectar formato → resolver parser.
2. `parse` → `ParsedStatement`.
3. **Validar a conta declarada** (senão, aborta sem persistir — D-09, D-13):
   - Tipo: extrato de cartão só em conta `CARTAO`; extrato de conta só nas demais.
   - Banco: `BANKID` do arquivo igual ao código da instituição (ignorando zeros à esquerda).
   - Número: `ACCTID` vinculado à conta na 1ª importação e conferido nas seguintes.
   - *Checksum:* não se aplica ao OFX (só traz o saldo final, sem saldo inicial nem totais). Fica para o CSV (Apêndice A).
4. Delegar ao `TransacaoService` a dedup + persistência → recebe quais são novas.
5. Delegar ao `CategorizacaoService` a categorização das novas (Etapa 5).
6. Gravar auditoria `Importacao` (com `LEDGERBAL` — D-10) e devolvê-la como resumo (lidas, novas, duplicadas, erros).

### 7.5 Atomicidade (resolve D-01 para o MVP)
Decisão MVP: importação **atômica por arquivo** (tudo ou nada), envolvendo os passos 3–6 em uma transação de banco. Se algo falhar, nada é persistido (nem o vínculo do número da conta) e o erro é reportado (HTTP 422). A alternativa (importar as linhas boas e reportar as ruins) fica registrada como possível evolução, caso surja necessidade real.

**Auditoria da falha (RNF12).** A tentativa que falhou também é registrada (`resultado = FALHA`, `erros = 1`). Como o rollback apagaria esse registro, o `ImportService` controla as transações com `TransactionTemplate`: leitura e parse fora de transação; passos 3–6 numa transação; se ela falhar, o registro da falha é gravado **depois**, numa transação própria. Upload acima de 5 MB é barrado antes (413) e não gera registro.

---

## 8. Cliente Android

### 8.1 Papel
Cliente fino da API REST (ADR-006). Não contém regra de negócio nem banco local no MVP: apresenta dados, coleta entrada do usuário e envia arquivos. Funciona apenas com conexão.

### 8.2 Stack
| Tema | Escolha | Observação |
|---|---|---|
| Linguagem | Kotlin | |
| UI | Jetpack Compose + Material 3 | |
| Arquitetura de UI | MVVM com fluxo unidirecional: `ViewModel` expõe `StateFlow<UiState>` | ViewModel testável sem emulador |
| Navegação | Navigation Compose | |
| Injeção de dependência | Hilt | |
| Rede | Retrofit + OkHttp + kotlinx.serialization | Interceptor do OkHttp injeta o token |
| Paginação | Paging 3 | Casa com RNF03 |
| Gráficos | Biblioteca de gráficos para Compose (ex.: Vico) | D-08 |
| Seleção de arquivo | Storage Access Framework (`OpenDocument`) | Não exige permissão de armazenamento |
| Credencial | DataStore + criptografia via Android Keystore | RNF26 |
| Ambientes | `BuildConfig` por build type: debug → backend local (`http://10.0.2.2:8080` no emulador, HTTP liberado só no debug); release → EC2 via HTTPS | RNF21, RNF25 |

### 8.3 Contrato com o backend
- A especificação OpenAPI (RNF22) é o contrato. Mudança de API começa no backend e na spec; o app acompanha. Gerar o cliente com openapi-generator é opcional.
- A especificação exportada fica em `fintrack-openapi.json` e o guia de uso pelo app em `fintrack-android-api.md` (autenticação, erros, paginação, endpoints por tela).
- **Dinheiro trafega como string decimal** (ex.: `"-123.45"`) e vira `BigDecimal` no app — nunca `Double` (estende o ADR-003 ao cliente). Formatação pt-BR (`R$ 1.234,56`) só na camada de UI.
- Datas em ISO-8601 (`yyyy-MM-dd`).
- Erros no formato Problem Details (RFC 9457, suporte nativo via `ProblemDetail` no Spring). O app exibe o campo `detail`.
- `401` em qualquer chamada → app leva o usuário à tela de configuração.

### 8.4 Telas (mapeadas nos fluxos)
| Tela | Fluxo | Requisitos |
|---|---|---|
| Configuração (servidor + token) | — | RF30 |
| Contas (lista, cadastro, edição, inativar) | A | RF01–RF03 |
| Importar extrato (conta + arquivo → resumo) | B | RF04–RF09, RF29 |
| Histórico de importações | B | RF08, RNF12 |
| Transações (lista paginada, filtros, detalhe com enriquecimento e transferência interna) | C, D | RF11–RF14, RF18, RF20 |
| Categorias e regras (cadastro, reaplicar em lote) | C | RF15–RF19 |
| Dashboard / relatórios | D | RF21–RF26 |

Toda tela trata os estados: carregando, conteúdo, vazio e erro.

### 8.5 Estrutura de pacotes
Organizada por feature (no app; o backend usa camadas — ADR-009).

```
com.fintrack.android
├── core/
│   ├── network/      (Retrofit, interceptor de token, mapeamento de erros)
│   ├── data/         (armazenamento seguro do token, config do servidor)
│   ├── model/        (helpers de BigDecimal, formatação pt-BR)
│   └── ui/           (tema Material 3, componentes reutilizáveis)
└── feature/
    ├── settings/
    ├── accounts/
    ├── importing/
    ├── transactions/
    ├── categorization/
    └── reports/
        (cada feature: data/ → api + repository · ui/ → screens + ViewModel)
```

---

## 9. Infraestrutura e implantação

### 9.1 Decisões
| Tema | Decisão | Motivo |
|---|---|---|
| Banco de dados | PostgreSQL | Relacional casa com o modelo; `UNIQUE` constraint dá a dedup de graça; bom para estudo. |
| Banco em teste | H2 (ou Testcontainers) | Testes rápidos sem infra externa. |
| Empacotamento | Docker + docker compose (app + Postgres + Caddy), sobe com um comando (RNF19). | Objetivo de praticar Docker; portabilidade (RNF20). |
| Hospedagem | Uma instância AWS EC2 com Elastic IP (ADR-007). | Acesso pelo celular de qualquer lugar. O IP público padrão muda ao parar/iniciar a instância; o Elastic IP não. |
| Borda / TLS | Caddy como proxy reverso, com HTTPS automático (Let's Encrypt). | Configuração mínima; exige um domínio apontando para o Elastic IP (D-07). |
| Config | Variáveis de ambiente / perfis Spring (RNF09, RNF21). | Sem segredo hardcoded; troca de ambiente sem recompilar. |
| Documentação de API | OpenAPI/Swagger (RNF22). | API autodocumentada; contrato com o app. |
| Backup | `pg_dump` agendado, enviado para fora da instância (ex.: S3). | RNF07. Backup dentro da própria EC2 some junto com ela. |

### 9.2 Topologia na EC2

```
Internet
   │  443 (HTTPS) · 80 só para emissão do certificado e redirect
   ▼
┌──────────────────── EC2 (docker compose) ────────────────────┐
│  caddy ──► fintrack-api (Spring Boot :8080) ──► postgres :5432 │
│  rede interna do compose — só o caddy publica portas          │
└───────────────────────────────────────────────────────────────┘
```

### 9.3 Regras de segurança da borda
- Security group: 443 e 80 abertos; 22 apenas para o IP do desenvolvedor; 5432 e 8080 **nunca** publicados.
- Toda rota exige `Authorization: Bearer <token>`, exceto `/actuator/health`. Swagger UI desabilitado no perfil de produção (ou atrás do token).
- Token aleatório (≥ 32 caracteres) vindo da variável de ambiente `FINTRACK_TOKEN`; comparação em tempo constante. Trocar o token = trocar a variável e reiniciar o container.
- Logs sem dados sensíveis (RNF08) e sem o token.

**Como ficou implementado (Etapa 8, pacote `security`):**
- `TokenAuthenticationFilter` (cadeia do Spring Security, não é `@Component`): lê `Authorization: Bearer <token>` (esquema sem diferenciar maiúsculas) e compara o SHA-256 do recebido com o do esperado via `MessageDigest.isEqual` — tempo constante e sem revelar o tamanho do token.
- Sem token ou com token errado: **401** em `ProblemDetail`, com `WWW-Authenticate: Bearer`, mesma mensagem nos dois casos. Token errado gera log `WARN` com método, rota e IP (nunca o cabeçalho).
- `SegurancaProperties` valida na subida: sem `FINTRACK_TOKEN` ou com menos de 32 caracteres, a aplicação não inicia.
- Sem sessão (`STATELESS`), sem CSRF (não há cookie; cliente nativo), sem form login nem HTTP Basic, sem usuário padrão gerado pelo Boot.
- Rotas abertas: `/actuator/health` (sem detalhes — RNF14), `/error` e a documentação, que no perfil `prod` está desligada (404). Console do H2 só no perfil `dev`.
- Perfil `prod` (`application-prod.yaml`): banco por `FINTRACK_DB_URL`/`USER`/`PASSWORD`, sem Swagger, sem console H2, `forward-headers-strategy: framework` (atrás do Caddy).
- Gerar um token: `openssl rand -base64 32` (disponível no Git Bash).

---

## 10. Registro de Decisões de Arquitetura (ADRs)

> ADR-001 (Monólito modular) está registrado no SRS. Abaixo, os novos.

### ADR-002 — Importação por Strategy + Factory, convergindo no modelo canônico
**Status:** Aceito · **Data:** 2026-07-07
**Contexto.** Cada banco entrega formato/estrutura diferente (OFX padronizado; CSV e PDF específicos por banco). Cogitou-se converter CSV → OFX para reusar um único parser.
**Decisão.** Cada origem tem seu próprio parser (Strategy) que produz **diretamente** o modelo canônico. Um resolver (Factory) escolhe o parser por (instituição, formato). **Não** se converte CSV para OFX.
**Consequências.** (+) Baixo acoplamento, extensível (novo banco = nova classe, Aberto/Fechado); o canônico é a única língua franca interna. (−) É preciso manter N parsers. (Rejeitado: CSV→OFX→canônico adiciona um hop e herda as manias do OFX sem ganho.)
**Nota (v0.2).** No MVP existe apenas o parser OFX. A estrutura continua valendo: o CSV do Mercado Pago entra no futuro como uma nova classe, sem alterar código existente (Apêndice A).

### ADR-003 — Dinheiro como `BigDecimal`, nunca `double`
**Status:** Aceito · **Data:** 2026-07-07
**Contexto.** Valores em ponto flutuante acumulam erro de arredondamento; inaceitável em finanças. O CSV do MP ainda vem em formato pt-BR (`6.731,50`).
**Decisão.** Todo valor monetário é `BigDecimal`, parseado com locale pt-BR explícito. Um value object `Money` em `common` centraliza isso. *(Nota v0.3: com o ADR-009, `Money` saiu do plano; o `BigDecimal` é usado direto. A regra do ADR continua valendo.)*
**Consequências.** (+) Correção financeira; checksum confiável. (−) Um pouco mais verboso que `double`.

### ADR-004 — Respeitar o `CHARSET`/`ENCODING` do cabeçalho OFX (resolve D-02)
**Status:** Aceito · **Data:** 2026-07-07
**Contexto.** O OFX real do Itaú declara `ENCODING:USASCII` / `CHARSET:1252` (Windows-1252), não UTF-8. Ler como UTF-8 corromperia acentos em `MEMO`.
**Decisão.** O parser lê o encoding declarado no cabeçalho e decodifica o arquivo de acordo. *(Correção v0.4: a ofx4j **não** trata isso — lê tudo como ISO-8859-1, o que quebra UTF-8 e os caracteres 0x80–0x9F do Windows-1252. O `OfxCharset` aplica a regra: v1 → `ENCODING`/`CHARSET` do cabeçalho, padrão Windows-1252; v2 → `encoding` da declaração XML, padrão UTF-8. Coberto por testes com arquivos em Windows-1252 e UTF-8.)*
**Consequências.** (+) Acentos corretos. (−) Não confiar cegamente no default do sistema.

### ADR-005 — Chave de dedup = ID do provedor, com hash de conteúdo como fallback (rebaixa D-04)
**Status:** Aceito · **Data:** 2026-07-07
**Contexto.** Temia-se que CSV não tivesse ID estável. Verificou-se que Itaú/Nubank (FITID) e Mercado Pago (`REFERENCE_ID`) **têm** ID estável.
**Decisão.** `dedup_key` = `providerTransactionId` quando presente; senão, hash de `conta+data+valor+descrição`. Constraint única `(conta_id, dedup_key)`.
**Consequências.** (+) Dedup limpa para os bancos reais (Itaú e Nubank no MVP; Mercado Pago quando o CSV entrar); risco de falso-positivo por hash não se aplica a eles. (−) O fallback por hash, se um dia usado, pode colidir em transações idênticas no mesmo dia — risco aceito e documentado.

### ADR-006 — Cliente Android nativo, fino e online-only
**Status:** Aceito · **Data:** 2026-10-20
**Contexto.** A v0.1 previa uso no PC. O uso real será no celular. O Spring Boot não roda no Android.
**Decisão.** A interface do MVP é um app Android nativo (Kotlin + Jetpack Compose) que consome a API REST. O app não tem banco local nem regra de negócio e só funciona com conexão.
**Consequências.** (+) Uma única fonte da verdade; backend, módulos e ADRs anteriores ficam intactos; app simples. (−) Sem uso offline; relatórios dependem da rede. (Rejeitados: tudo no dispositivo com Room — descartaria Spring e Docker, objetivos do projeto; sincronização offline — complexidade desproporcional para o MVP.)

### ADR-007 — Backend em AWS EC2 acessível pela internet
**Status:** Aceito · **Data:** 2026-10-20
**Contexto.** O app precisa alcançar o backend fora de casa. Alternativas: só rede local (limita o uso); VPN privada como Tailscale (nada fica exposto, mas exige cliente VPN no celular); exposição pública.
**Decisão.** Uma instância EC2 com docker compose, Elastic IP e acesso pela internet.
**Consequências.** (+) Acesso de qualquer lugar sem software extra; prática de cloud e deploy. (−) Dados financeiros ficam alcançáveis pela internet: isso invalida a premissa original de "sem exposição pública" e obriga autenticação e HTTPS já no MVP (ADR-008). Custo mensal da instância. Responsabilidade por atualizar o sistema operacional e as imagens.

### ADR-008 — Token único de acesso e HTTPS obrigatório no MVP
**Status:** Aceito · **Data:** 2026-10-20
**Contexto.** Com o ADR-007 a API fica pública. Sem autenticação, qualquer um que descubra o endereço lê e altera os dados. Além disso, o Android bloqueia HTTP em texto puro por padrão.
**Decisão.** Token de acesso único (single-user) enviado em `Authorization: Bearer <token>`, validado por um filtro do Spring Security antes dos controllers — o "filtro na borda" previsto no RNF11, antecipado. TLS terminado no Caddy com certificado Let's Encrypt, o que exige um domínio apontando para o Elastic IP (D-07).
**Consequências.** (+) Proteção mínima adequada a dado financeiro exposto; o filtro na borda mantém o caminho aberto para login real na Fase 2. (−) Token estático não expira: se vazar, precisa ser trocado manualmente. Necessário registrar um domínio.

### ADR-009 — Camadas MVC em vez de módulos internos
**Status:** Aceito · **Data:** 2026-10-20 · **Altera:** ADR-001 (organização interna), §3 e §6 da v0.2 · **Resolve:** D-06
**Contexto.** A v0.2 dividia o monólito em módulos (`accounts`, `importing`, `transactions`, `categorization`, `reporting`), cada um com camadas `domain`/`application`/`infrastructure` e entidade de domínio separada da entidade JPA. Para um projeto single-user com um desenvolvedor, isso multiplica classes (domínio + entidade JPA + mapper + porta + adapter por agregado) e atrasa a entrega. O protótipo já usa camadas.
**Decisão.** Organizar o backend em camadas MVC: `controller` → `service` → `repository`, com entidades JPA como modelo (sem classe de domínio separada). O sistema continua sendo um monólito (ADR-001). Os parsers continuam por Strategy + Factory (ADR-002), no pacote `parser`.
**Consequências.** (+) Menos classes e menos mapeamento; padrão conhecido do desenvolvedor e do mercado; protótipo aproveitável. (+) RNF17 continua atendido: entidades testadas como objetos comuns e services com Mockito, sem Spring nem banco. (−) As fronteiras entre áreas do sistema não são garantidas pela estrutura; um service pode acabar acessando o que não deveria. Mitigação: regras da §3.3 (repository com service "dono", sem ciclos) e code review. (−) A extração da importação na Fase 2 fica mais trabalhosa (exige separar classes antes). (−) O value object `Money` sai do plano: dinheiro é `BigDecimal` direto nas entidades (o ADR-003 continua valendo).

---

## 11. Rastreabilidade (requisito → onde é atendido)

| Requisito | Atendido em |
|---|---|
| RF01–RF03 | `ContaController`/`ContaService`; entidade Conta, enum Instituicao |
| RF04–RF09 | `ImportController`/`ImportService` + pacote `parser`; Fluxo B; §7 |
| RF07 / RNF04 | `dedup_key` + constraint única (§5.2); ADR-005 |
| RF11 / RNF06 | Separação bruto imutável vs. enriquecimento (§5.1) |
| RF14 | Campo `transferenciaInterna` (§5.1) |
| RF15–RF20 | `CategorizacaoService`, `CategorizadorPorRegras`, `CategoriaController`, `RegraController`; reaplicação em `TransacaoService` |
| RF21–RF26 | `RelatorioController`/`RelatorioService`; somas no banco via `RelatorioRepository`; saldos pelo `LEDGERBAL` (D-10) |
| RNF05 / D-01 | Importação atômica (§7.5) |
| D-02 | ADR-004 |
| RNF17 | Entidades testáveis como objetos comuns + services com Mockito (§3.5, ADR-009) |
| RNF19–RNF21 | §9 (Docker, perfis, EC2) |
| RF28–RF30 | §8 (Cliente Android); ADR-006 |
| RNF10, RNF11, RNF25, RNF26 | §9.3; §8.2; ADR-007, ADR-008 |
| RNF03 (no app) | §8.2 (Paging 3) |

---

## 12. Decisões ainda em aberto

| ID | Assunto | Status |
|---|---|---|
| D-01 | Atomicidade parcial | **Resolvido p/ MVP** (atômico, §7.5); revisar se necessário |
| D-02 | Encoding OFX | **Resolvido** (ADR-004) |
| D-04 | Dedup sem ID | **Rebaixado** (ADR-005); não afeta os bancos reais |
| D-06 | Domínio puro vs. entidade JPA única | **Resolvido** (ADR-009) — entidade JPA única |
| D-09 | Conferência da conta declarada no OFX | **Resolvido** — `contas.numero_externo` (ACCTID), preenchido na 1ª importação e conferido nas seguintes |
| D-10 | Origem do saldo por conta (RF25) | **Resolvido** — `LEDGERBAL` da última importação com sucesso, exibido com a data |
| D-11 | Manual × regra no lote (RF18/RF19) | **Resolvido** — `transacoes.origem_categoria`; lote só altera sem categoria ou origem `REGRA` |
| D-12 | `Instituicao` tabela × enum | **Resolvido** — enum com código do banco |
| D-03 | Alvos numéricos de performance | Adiado (do SRS) |
| D-07 | Domínio para o HTTPS (registrar um próprio ou usar subdomínio de DNS dinâmico) | Em aberto — necessário antes do primeiro deploy na EC2 |
| D-08 | Biblioteca de gráficos no app | Em aberto — decidir ao implementar os relatórios no app |
| D-13 | Checksum da importação (§7.4) | **Resolvido** — não se aplica ao OFX (sem saldo inicial nem totais no arquivo); validação OFX = tipo + banco + número da conta. Checksum fica para o CSV |

---

## 13. Histórico de versões

| Versão | Data | Mudança |
|---|---|---|
| 0.1 | 2026-07-07 | Arquitetura inicial: AS-IS/TO-BE, 4 fluxos, monólito modular com mapa de dependências, modelo canônico (a partir de OFX Itaú + CSV Mercado Pago), modelo de dados, estrutura de pacotes, desenho do módulo de importação, ADR-002 a 005. |
| 0.2 | 2026-10-20 | Cliente Android nativo (§8, ADR-006). Deploy em EC2 com Caddy/HTTPS (§9, ADR-007). Autenticação por token antecipada para o MVP (ADR-008, pacote `security`). CSV do Mercado Pago removido do MVP: modelo canônico só OFX, mapeamento CSV movido para o Apêndice A. Seções 8–12 renumeradas para 9–13. |
| 0.3 | 2026-10-20 | Backend em camadas MVC sem módulos internos (ADR-009): §3.1–3.5 e §6 reescritas, rastreabilidade ajustada, D-06 resolvido, `Money` removido do plano. Diagramas movidos para `fintrack-modelo-dados.md`. |
| 0.4 | 2026-10-20 | Importação implementada (Etapa 4): §7.1 (`ImportContext` sem charset; ajustes de encoding, data e valor sobre a ofx4j), §7.3, §7.4 (validação da conta; checksum não se aplica ao OFX — D-13), §7.5 (auditoria da falha com `TransactionTemplate`). ADR-004 corrigido: a ofx4j não aplica o charset. |
| 0.5 | 2026-10-20 | §9.3: segurança implementada (Etapa 8) — filtro de token, 401 em ProblemDetail, health sem detalhes, perfil `prod`. |

---

## Apêndice A — Evolução Futura: CSV do Mercado Pago (RF10)

Análise feita na v0.1, preservada para quando o RF10 entrar. **Não faz parte do MVP.**

### A.1 Mapeamento para o modelo canônico
| Campo canônico | CSV Mercado Pago |
|---|---|
| `declaredAccount` | — (não há no arquivo) |
| `period` | derivável das linhas |
| `ledgerBalance` | `FINAL_BALANCE` |
| `summary` | `CREDITS`, `DEBITS` |
| `providerTransactionId` | `REFERENCE_ID` |
| `date` | `RELEASE_DATE` (`dd-MM-yyyy`) |
| `amount` | `TRANSACTION_NET_AMOUNT` (formato pt-BR, ex.: `6.731,50`) |
| `description` | `TRANSACTION_TYPE` |

### A.2 O que muda quando entrar
- `StatementFormat` ganha `CSV`; o `FormatDetector` passa a reconhecer texto delimitado.
- Nova classe `MercadoPagoCsvParser` (`@Component`): pula o bloco de resumo, separador `;`, número e data em pt-BR, mapeia `REFERENCE_ID`.
- Sem `declaredAccount`, a validação de conta do §7.4 não se aplica a esse formato; vale só o checksum.
- Nenhum código existente é alterado (ADR-002).

