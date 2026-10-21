# Fintrack — Guia técnico do app Android (contrato com o backend)

| Campo | Valor |
|---|---|
| Projeto | Fintrack |
| Versão do documento | 0.1 |
| Documentos relacionados | `fintrack-arquitetura.md` §8 (cliente Android), `fintrack-openapi.json` (especificação), `fintrack-srs.md` (RF28–RF30, RNF25, RNF26) |
| Backend de referência | branch `feat/etapa-8-seguranca` (commit `ff04197`) |
| Última atualização | 2026-10-20 |

> **Para que serve.** Este é o guia que o app Android segue para conversar com o backend: como autenticar, como ler cada resposta, como tratar cada erro e qual endpoint cada tela usa.
>
> **Fonte da verdade do contrato:** `fintrack-openapi.json`, exportado do backend. Este guia explica o que a especificação não diz (convenções, armadilhas, comportamento). Se os dois divergirem, a especificação vale e este guia está desatualizado. **Todos os exemplos JSON abaixo foram capturados do backend real**, rodando com os extratos sintéticos dos testes.
>
> O app é **cliente fino** (ADR-006): não tem regra de negócio nem banco local, não calcula totais e não decide categoria. Tudo isso vem do backend.

---

## 1. Ambientes e configuração

| Build | URL base | Transporte | Token |
|---|---|---|---|
| `debug` no emulador | `http://10.0.2.2:8080` (o `10.0.2.2` é o `localhost` do PC visto pelo emulador) | HTTP liberado **só no debug** | `dev-token-somente-local-nao-usar-em-producao` (token fixo do perfil `dev`) |
| `debug` em celular físico | `http://<IP do PC na rede local>:8080` | idem | idem |
| `release` | `https://<domínio>` — **pendente (D-07)** | **só HTTPS** (RNF25) | o de `FINTRACK_TOKEN` do servidor |

- URL e token são configurados pelo usuário na tela de **Configuração** (RF30). O `BuildConfig` só traz o valor sugerido de cada build type (arquitetura §8.2).
- **HTTP em texto puro só no `debug`:** `network_security_config` com `cleartextTrafficPermitted` em `src/debug/res/xml/`. O `release` não pode ter essa liberação (instruções do projeto, seção Segurança).
- Para rodar o backend local: `./mvnw spring-boot:run`. O perfil `dev` sobe sozinho, com H2 na memória e os dados apagados a cada reinício.

### 1.1 Validar a configuração (RF30)

Ao salvar servidor e token, o app confere os dois em sequência:

1. `GET /actuator/health` **sem token** → `200 {"status":"UP"}`. Se falhar, o problema é endereço ou rede.
2. `GET /api/v1/contas` **com token** → `200`. Se vier `401`, o problema é o token.

---

## 2. Autenticação (ADR-008)

- Todo request para `/api/v1/**` leva o cabeçalho `Authorization: Bearer <token>`.
- Sem token ou com token errado, a resposta é **401**, com o mesmo corpo nos dois casos:

```json
{
  "type": "about:blank",
  "title": "Não autorizado",
  "status": 401,
  "detail": "Token de acesso ausente ou inválido. Confira a configuração do app.",
  "instance": "/api/v1/contas"
}
```

- **401 em qualquer chamada → levar o usuário à tela de Configuração** (arquitetura §8.3). O jeito recomendado: um `Interceptor` do OkHttp que detecta 401 e publica um evento num `SharedFlow` global; o `NavHost` observa esse fluxo e navega.
- Armazenamento do token: DataStore com criptografia apoiada no **Android Keystore** (RNF26). Nunca no código, nunca no `BuildConfig` de release, nunca em log.
  - Atenção: a biblioteca `androidx.security:security-crypto` (`EncryptedSharedPreferences`) foi descontinuada pelo Google. Confira a recomendação atual na documentação oficial antes de escolher a forma de criptografar.

```kotlin
// core/network — injeta o token em toda requisição.
class TokenInterceptor @Inject constructor(
    private val credenciais: CredenciaisStore, // guarda o token descriptografado em memória (StateFlow)
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = credenciais.tokenAtual() ?: return chain.proceed(chain.request())
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
```

> O `intercept` é síncrono e roda fora da thread principal. Por isso o token precisa estar em memória (lido do DataStore uma vez e mantido num `StateFlow`), e não ser lido do disco a cada chamada.

---

## 3. Convenções do contrato

| Tema | Regra | No app |
|---|---|---|
| Dinheiro | Sempre **string decimal** com 2 casas: `"-42.10"`, `"4807.15"` (ADR-003, arquitetura §8.3) | `BigDecimal`. Nunca `Double`/`Float`, nem em teste |
| Sinal do valor | Em **transações**, negativo = débito. Em **relatórios**, `despesas` vêm **positivas** | Não inverta sinais por conta própria; use o campo `tipo` (`CREDITO`/`DEBITO`) |
| Data | `yyyy-MM-dd` (`"2026-06-30"`) | `LocalDate` |
| Data e hora | ISO-8601 em UTC, com fração de segundo de tamanho variável (`"2026-10-21T02:00:38.365243Z"`) | `Instant.parse` (aceita qualquer fração) |
| Mês | `yyyy-MM` (`"2026-06"`) | `YearMonth` |
| Enums | Strings em maiúsculas (`"CORRENTE"`, `"OFX_V1"`) | `enum class` com os mesmos nomes |
| Campos nulos | Vêm **presentes com `null`** (ex.: `"categoriaId": null`) | Propriedades anuláveis |
| Campos desconhecidos | O backend pode acrescentar campos (mudança não-quebradora) | `ignoreUnknownKeys = true` |
| Versão | Tudo em `/api/v1` | Mudança quebradora vira `/api/v2` |
| Ordenação | Fixa, definida pelo backend (ex.: transações mais recentes primeiro) | Não reordenar no app |

`java.time` (`LocalDate`, `Instant`, `YearMonth`) existe nativamente a partir da API 26. Com `minSdk` menor, ative o *core library desugaring*.

### 3.1 Serialização (kotlinx.serialization)

```kotlin
// core/model
object BigDecimalComoTexto : KSerializer<BigDecimal> {
    override val descriptor = PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: BigDecimal) = encoder.encodeString(value.toPlainString())
    override fun deserialize(decoder: Decoder) = BigDecimal(decoder.decodeString())
}
// Mesmo padrão para LocalDate (LocalDate.parse / toString), Instant e YearMonth.

val json = Json {
    ignoreUnknownKeys = true   // backend pode ganhar campos novos sem quebrar o app
    explicitNulls = false      // campo ausente = null
}
```

> Por que não `Double`? `0.1 + 0.2` em `Double` dá `0.30000000000000004`. Em dinheiro isso vira centavo sumindo no total (ADR-003).

### 3.2 Formatação

Só na camada de UI, nunca no ViewModel nem no repositório:

```kotlin
val moeda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
moeda.format(BigDecimal("-42.10")) // "-R$ 42,10" — o espaço depois de "R$" é um NBSP ( ), não um espaço comum;
                                   // lembre disso ao comparar strings em teste.
```

---

## 4. Erros (Problem Details — RFC 9457)

Todo erro vem em `application/problem+json`, com os mesmos campos. **O app mostra o `detail`**, que já é uma frase pronta para o usuário.

| Status | Quando | Corpo (exemplo real) | O que o app faz |
|---|---|---|---|
| 400 | Campo inválido no corpo ou na URL | `"detail": "apelido é obrigatório; tipo é obrigatório"` + `"campos": {"apelido": "é obrigatório", "tipo": "é obrigatório"}` | Marca cada campo do formulário pelo mapa `campos` |
| 400 | Parâmetro de consulta inválido | `"detail": "tamanho deve ser no máximo 100"`, `"campos": {"tamanho": "..."}` | Bug do app: logar e mostrar erro genérico |
| 400 | JSON malformado ou enum inexistente | `"detail": "Corpo da requisição inválido: JSON malformado ou valor não aceito."` | Bug do app (ou corpo fora de UTF-8) |
| 401 | Token ausente ou inválido | ver §2 | Ir para a tela de Configuração |
| 404 | Recurso não existe | `"detail": "Transação com id 999999 não existe."` | Mostrar `detail` e voltar |
| 409 | Conflito (ex.: nome repetido) | `"detail": "Já existe a categoria \"moradia\" neste nível."` | Mostrar `detail` no campo |
| 413 | Arquivo acima de 5 MB | `"detail": "O arquivo passa do tamanho máximo de 5 MB."` | Mostrar `detail`; o ideal é checar o tamanho antes de enviar |
| 422 | Importação recusada (arquivo inválido, conta errada, conta inativa) | `"detail": "O arquivo é um extrato de cartão de crédito, mas a conta escolhida não é de cartão."` | Mostrar `detail` |
| 500 | Erro inesperado | `"detail": "Ocorreu um erro inesperado. Tente novamente."` | Mostrar `detail` e oferecer "tentar de novo" |
| — | Sem rede / timeout | (sem resposta) | Mensagem própria do app (`strings.xml`) |

Exemplo completo (400 com `campos`):

```json
{
  "type": "about:blank",
  "title": "Dados inválidos",
  "status": 400,
  "detail": "apelido é obrigatório; tipo é obrigatório",
  "instance": "/api/v1/contas",
  "campos": { "apelido": "é obrigatório", "tipo": "é obrigatório" }
}
```

Sugestão de modelo no app:

```kotlin
@Serializable
data class ProblemaDto(
    val status: Int,
    val title: String? = null,
    val detail: String? = null,
    val campos: Map<String, String> = emptyMap(),
)

sealed interface ErroApi {
    data object SemRede : ErroApi
    data object NaoAutorizado : ErroApi                                   // 401
    data class Validacao(val mensagem: String, val campos: Map<String, String>) : ErroApi // 400
    data class Negocio(val mensagem: String) : ErroApi                    // 404, 409, 413, 422
    data class Inesperado(val mensagem: String) : ErroApi                 // 500 e o resto
}
```

O repositório converte `HttpException`/`IOException` em `ErroApi`. O ViewModel só conhece `ErroApi` e põe no `UiState`.

---

## 5. Paginação (RNF03) — só em transações

`GET /api/v1/transacoes` devolve:

```json
{
  "conteudo": [ /* TransacaoDto */ ],
  "pagina": 0,
  "tamanho": 2,
  "totalElementos": 5,
  "totalPaginas": 3,
  "ultima": false
}
```

- Parâmetros `pagina` (começa em **0**) e `tamanho` (padrão 20, **máximo 100**; acima disso, 400).
- Com Paging 3, a chave é o número da página. **Armadilha:** por padrão o Paging 3 pede `3 × pageSize` na primeira carga. Com API paginada por número de página, isso desalinha as páginas seguintes (a "página 1" do backend repetiria itens). Use `initialLoadSize = pageSize`:

```kotlin
Pager(PagingConfig(pageSize = 20, initialLoadSize = 20, enablePlaceholders = false)) {
    TransacoesPagingSource(api, filtro)
}

class TransacoesPagingSource(
    private val api: TransacaoApi,
    private val filtro: FiltroTransacoes,
) : PagingSource<Int, TransacaoDto>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TransacaoDto> = try {
        val pagina = params.key ?: 0
        val resposta = api.listar(filtro.toQueryMap(), pagina, params.loadSize.coerceAtMost(100))
        LoadResult.Page(
            data = resposta.conteudo,
            prevKey = if (pagina == 0) null else pagina - 1,
            nextKey = if (resposta.ultima) null else pagina + 1,
        )
    } catch (e: Exception) {
        LoadResult.Error(e)
    }

    override fun getRefreshKey(state: PagingState<Int, TransacaoDto>): Int? =
        state.anchorPosition?.let { state.closestPageToPosition(it)?.prevKey?.plus(1) }
}
```

As demais listas (contas, categorias, regras, histórico de importações, relatórios) **não** são paginadas: vêm inteiras.

---

## 6. Endpoints

Todos exigem token, exceto `GET /actuator/health`. Corpo e resposta em JSON, exceto o upload (multipart).

### 6.1 Contas (RF01–RF03)

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| `POST` | `/api/v1/contas` | `{"instituicao", "tipo", "apelido"}` | **201** + `Location` + `ContaDto` |
| `GET` | `/api/v1/contas?ativa=true` | — | `ContaDto[]` ordenado por apelido; `ativa` opcional (ausente = todas) |
| `GET` | `/api/v1/contas/{id}` | — | `ContaDto` / 404 |
| `PATCH` | `/api/v1/contas/{id}` | `{"apelido"}` | `ContaDto`. **Só o apelido é editável** |
| `POST` | `/api/v1/contas/{id}/inativar` | — | **204** sem corpo. Não existe excluir nem reativar (RF02) |

- `instituicao`: `ITAU`, `NUBANK`, `MERCADOPAGO`.
- `tipo`: `CORRENTE`, `POUPANCA`, `CARTAO`.
- `apelido`: obrigatório, até 60 caracteres. A moeda é sempre BRL.

```json
// ContaDto — resposta do POST
{
  "id": 1,
  "instituicao": "ITAU",
  "codigoInstituicao": "0341",
  "tipo": "CORRENTE",
  "apelido": "Itaú corrente",
  "moeda": "BRL",
  "ativa": true
}
```

### 6.2 Importação de extrato (RF04–RF09, RF29)

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| `POST` | `/api/v1/contas/{contaId}/importacoes` | `multipart/form-data`, campo **`arquivo`** | **201** + `ImportacaoDto` |
| `GET` | `/api/v1/contas/{contaId}/importacoes` | — | `ImportacaoDto[]`, mais recente primeiro, **inclui as falhas** |
| `GET` | `/api/v1/contas/{contaId}/importacoes/{id}` | — | `ImportacaoDto` |

```json
// ImportacaoDto — resposta do upload (resumo do RF08)
{
  "id": 1,
  "nomeArquivo": "extrato-junho.ofx",
  "formato": "OFX_V1",
  "dataHora": "2026-10-21T02:00:38.365243100Z",
  "totalLido": 3,
  "novas": 3,
  "duplicadas": 0,
  "erros": 0,
  "resultado": "SUCESSO",
  "saldoDeclarado": "4807.15",
  "dataSaldo": "2026-06-30"
}
```

Comportamento que a tela precisa refletir:

- **Tudo ou nada.** Se o arquivo for recusado (**422**), nada foi gravado. A tentativa aparece no histórico com `"resultado": "FALHA"`, `"erros": 1`, `"formato"` podendo ser `null` (formato não reconhecido) e saldo nulo.
- **Reimportar é seguro.** O mesmo arquivo de novo devolve `novas: 0` e `duplicadas: N`. Vale mostrar isso como sucesso ("nenhuma transação nova").
- O backend confere se o arquivo é **da conta escolhida** (tipo conta × cartão, banco e número da conta). Por isso a tela pede a conta **antes** do arquivo.
- As transações novas já chegam categorizadas pelas regras existentes (RF17).

Seleção e envio do arquivo (RF29):

```kotlin
// UI: Storage Access Framework — não precisa de permissão de armazenamento.
val seletor = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    uri?.let(viewModel::arquivoEscolhido)
}
// O MIME de OFX varia entre aparelhos e bancos; o backend valida o conteúdo de qualquer forma.
seletor.launch(arrayOf("application/x-ofx", "application/ofx", "application/octet-stream", "text/plain", "text/xml", "application/xml"))

// Repositório (Dispatchers.IO): nome, tamanho e bytes via ContentResolver.
val nome = /* OpenableColumns.DISPLAY_NAME */
val tamanho = /* OpenableColumns.SIZE — se > 5 MB, nem envie (o backend responderia 413) */
val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
val parte = MultipartBody.Part.createFormData(
    "arquivo", nome, bytes.toRequestBody("application/octet-stream".toMediaType()),
)
api.importar(contaId, parte)
```

### 6.3 Transações (RF11–RF14, RF18, RF20)

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| `GET` | `/api/v1/transacoes` | — | `PaginaDto<TransacaoDto>` (ver §5) |
| `GET` | `/api/v1/transacoes/{id}` | — | `TransacaoDto` |
| `PUT` | `/api/v1/transacoes/{id}/categoria` | `{"categoriaId"}` | `TransacaoDto` com `origemCategoria: "MANUAL"` (RF18) |
| `DELETE` | `/api/v1/transacoes/{id}/categoria` | — | `TransacaoDto` sem categoria (RF20) |
| `PATCH` | `/api/v1/transacoes/{id}` | `{"nota"?, "tags"?, "transferenciaInterna"?}` | `TransacaoDto` |

Filtros do `GET` (todos opcionais e combináveis — RF12):

| Parâmetro | Exemplo | Observação |
|---|---|---|
| `contaId` | `1` | |
| `categoriaId` | `1` | **Inclui as subcategorias** (filtrar "Alimentação" traz "Padaria") |
| `semCategoria` | `true` | Só "Não categorizado". Não combine com `categoriaId` (400) |
| `tipo` | `DEBITO` | `CREDITO` ou `DEBITO` |
| `dataInicio`, `dataFim` | `2026-06-01` | Inclusivos; início depois do fim → 400 |
| `texto` | `padaria` | Trecho da descrição; ignora maiúsculas, mas **não** acentos |
| `pagina`, `tamanho` | `0`, `20` | §5 |

```json
// TransacaoDto
{
  "id": 3,
  "contaId": 1,
  "contaApelido": "Itaú corrente",
  "data": "2026-06-30",
  "valor": "-42.10",
  "tipo": "DEBITO",
  "descricao": "PADARIA SÃO JOÃO – CAFÉ",
  "categoriaId": 2,
  "categoriaNome": "Padaria",
  "origemCategoria": "REGRA",
  "nota": null,
  "tags": [],
  "transferenciaInterna": false
}
```

Regras da tela de detalhe:

- **Data, valor e descrição são do banco e não podem ser editados** (RF11, RNF06). Mostre-os como texto, não como campo editável. O `PATCH` ignora esses campos.
- **`PATCH` parcial:** campo ausente = não muda. `tags` substitui a lista inteira (lista vazia apaga). `nota: ""` apaga a nota.
  - Limites: nota até 500 caracteres; até 20 tags; cada tag até 40 caracteres, normalizada pelo backend para minúsculas e sem repetição.
- **Categoria tem endpoint próprio:** `PUT` para escolher (vira `MANUAL`, e as regras não mexem mais nela) e `DELETE` para voltar a "Não categorizado".
- `origemCategoria`: `MANUAL`, `REGRA` ou `null`. Vale mostrar um indicador visual para "categorizada automaticamente".
- `transferenciaInterna: true` tira a transação de todos os relatórios (RF14).

### 6.4 Categorias (RF15)

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| `POST` | `/api/v1/categorias` | `{"nome", "categoriaPaiId"?}` | **201** + `CategoriaDto` |
| `GET` | `/api/v1/categorias` | — | Árvore: raízes ordenadas por nome, com `subcategorias` dentro |
| `GET` | `/api/v1/categorias/{id}` | — | `CategoriaDto` |
| `PATCH` | `/api/v1/categorias/{id}` | `{"nome"}` | `CategoriaDto` (renomear) |

- **No máximo 2 níveis.** Só se cria subcategoria de categoria raiz; tentar um 3º nível dá 400.
- Nome único por nível, sem diferenciar maiúsculas (409). Até 60 caracteres.
- **Não existe excluir categoria.**

```json
// GET /api/v1/categorias
[
  {
    "id": 1, "nome": "Alimentação", "categoriaPaiId": null,
    "subcategorias": [ { "id": 2, "nome": "Padaria", "categoriaPaiId": 1, "subcategorias": [] } ]
  },
  { "id": 3, "nome": "Moradia", "categoriaPaiId": null, "subcategorias": [] }
]
```

### 6.5 Regras de categorização (RF16–RF19)

| Método | Rota | Corpo | Resposta |
|---|---|---|---|
| `POST` | `/api/v1/regras` | `{"categoriaId", "operador", "padrao", "prioridade"}` | **201** + `RegraDto` |
| `GET` | `/api/v1/regras` | — | `RegraDto[]` na ordem em que são avaliadas |
| `DELETE` | `/api/v1/regras/{id}` | — | **204** |
| `POST` | `/api/v1/regras/reaplicar` | — | `{"avaliadas": 4, "alteradas": 0}` |

- **`operador`:**
  - `CONTAINS` e `STARTS_WITH` ignoram maiúsculas **e acentos** ("pagto agua" casa com "PAGTO ÁGUA").
  - `REGEX` ignora maiúsculas e é validada na criação (400 se inválida).
- **`prioridade`:** inteiro ≥ 0. A menor é avaliada primeiro, e a primeira regra que casa vence.
- **Regra nova só vale para as próximas importações.** Para aplicar no histórico, chame `reaplicar`. Sugestão: depois de criar ou excluir uma regra, perguntar "Aplicar às transações existentes?".
- O `reaplicar` **nunca** altera categoria `MANUAL`. Transação categorizada por uma regra que deixou de casar volta a "Não categorizado".

```json
// RegraDto
{ "id": 1, "categoriaId": 2, "categoriaNome": "Padaria", "operador": "CONTAINS", "padrao": "padaria", "prioridade": 10 }
```

### 6.6 Relatórios (RF21–RF26)

Todos somente leitura. Transferências internas ficam fora (RF14). **Despesas vêm positivas**; `saldo = receitas − despesas`.

| Rota | Parâmetros | Resposta |
|---|---|---|
| `GET /api/v1/relatorios/resumo` | `contaId`, `categoriaId`, `dataInicio`, `dataFim` (todos opcionais) | `{"receitas","despesas","saldo"}` |
| `GET /api/v1/relatorios/por-categoria` | idem | Lista por categoria raiz, com `subcategorias` dentro; mais despesa primeiro; "Não categorizado" (`categoriaId: null`) por último |
| `GET /api/v1/relatorios/mensal` | `mesInicio`, `mesFim` (**obrigatórios**, `yyyy-MM`, até 36 meses), `contaId`, `categoriaId` | Um item por mês, **inclusive meses zerados**. Pronto para o gráfico, sem preencher lacunas no app |
| `GET /api/v1/relatorios/comparativo` | `meses=2026-05,2026-06` (2 a 12 meses distintos), `contaId`, `categoriaId` | Um item por mês, em ordem, com `porCategoria` preenchido |
| `GET /api/v1/relatorios/saldos` | — | Saldo de cada conta ativa e o consolidado |

```json
// resumo de junho
{ "receitas": "3589.90", "despesas": "282.75", "saldo": "3307.15" }

// por-categoria
[
  { "categoriaId": 3, "nome": "Moradia", "receitas": "0.00", "despesas": "150.75", "quantidade": 1, "subcategorias": [] },
  { "categoriaId": 1, "nome": "Alimentação", "receitas": "0.00", "despesas": "42.10", "quantidade": 1,
    "subcategorias": [ { "categoriaId": 2, "nome": "Padaria", "receitas": "0.00", "despesas": "42.10", "quantidade": 1, "subcategorias": [] } ] },
  { "categoriaId": 4, "nome": "Renda", "receitas": "3500.00", "despesas": "0.00", "quantidade": 1, "subcategorias": [] },
  { "categoriaId": null, "nome": "Não categorizado", "receitas": "89.90", "despesas": "89.90", "quantidade": 2, "subcategorias": [] }
]

// mensal (mesInicio=2026-05, mesFim=2026-06)
[
  { "mes": "2026-05", "receitas": "0.00", "despesas": "0.00", "saldo": "0.00", "porCategoria": [] },
  { "mes": "2026-06", "receitas": "3589.90", "despesas": "282.75", "saldo": "3307.15", "porCategoria": [] }
]

// saldos
{
  "consolidado": "3572.59",
  "contas": [
    { "contaId": 1, "apelido": "Itaú corrente", "tipo": "CORRENTE", "saldo": "4807.15", "dataSaldo": "2026-06-30" },
    { "contaId": 2, "apelido": "Nubank cartão", "tipo": "CARTAO", "saldo": "-1234.56", "dataSaldo": "2026-06-30" }
  ]
}
```

Pontos de atenção na tela de relatórios:

- **Por categoria** traz despesas **e** receitas por categoria. O estorno de uma compra no cartão cai na mesma categoria dela. Para "quanto gastei", mostre `despesas − receitas` da categoria, ou as duas colunas. Não some só `despesas`.
- **Saldos:** o saldo é o informado pelo banco no extrato mais recente, **na data `dataSaldo`**. Mostre a data ("Saldo em 30/06/2026"): o saldo não é em tempo real. Conta que nunca foi importada vem com `saldo: null` e fica fora do consolidado. Saldo de cartão costuma ser negativo, porque é dívida.
- Biblioteca de gráficos: decisão em aberto (**D-08**).

### 6.7 Health check

`GET /actuator/health`, **sem token**, responde `{"status":"UP"}`. Só serve para a validação da Configuração (§1.1).

---

## 7. Telas × endpoints (arquitetura §8.4)

| Tela | Endpoints | Requisitos |
|---|---|---|
| Configuração (servidor + token) | `GET /actuator/health`, `GET /api/v1/contas` | RF30 |
| Contas (lista, cadastro, edição, inativar) | `GET/POST /contas`, `GET/PATCH /contas/{id}`, `POST /contas/{id}/inativar` | RF01–RF03 |
| Importar extrato (conta + arquivo → resumo) | `GET /contas?ativa=true`, `POST /contas/{id}/importacoes` | RF04–RF09, RF29 |
| Histórico de importações | `GET /contas/{id}/importacoes` | RF08, RNF12 |
| Transações (lista paginada, filtros) | `GET /transacoes`, `GET /contas`, `GET /categorias` (para os filtros) | RF12, RNF03 |
| Detalhe da transação | `GET /transacoes/{id}`, `PUT/DELETE /transacoes/{id}/categoria`, `PATCH /transacoes/{id}` | RF11, RF13, RF14, RF18 |
| Categorias e regras | `GET/POST /categorias`, `PATCH /categorias/{id}`, `GET/POST /regras`, `DELETE /regras/{id}`, `POST /regras/reaplicar` | RF15–RF19 |
| Dashboard / relatórios | `GET /relatorios/*` | RF21–RF26 |

Toda tela trata os quatro estados: **carregando, conteúdo, vazio e erro** (instruções do projeto). Exemplo de `UiState`:

```kotlin
sealed interface ContasUiState {
    data object Carregando : ContasUiState
    data class Conteudo(val contas: List<ContaUi>) : ContasUiState
    data object Vazio : ContasUiState                     // "Cadastre sua primeira conta"
    data class Erro(val erro: ErroApi) : ContasUiState    // com ação "tentar de novo"
}
```

---

## 8. Regras para o app

1. **Sem regra de negócio no app** (ADR-006). Não calcular totais, saldos nem categorias: usar os relatórios. Não decidir se uma transação é débito: usar `tipo`.
2. **Dinheiro é `BigDecimal` de ponta a ponta.** A string do JSON vira `BigDecimal` no DTO, e só vira texto `R$` na UI.
3. **Nada sensível em log** (RNF08): nem token, nem valores, descrições ou números de conta. No `HttpLoggingInterceptor`:
   - nível no máximo `BASIC`, e só no `debug`;
   - `redactHeader("Authorization")`;
   - **nunca `BODY`**, que imprimiria extratos inteiros no Logcat.
4. **HTTPS obrigatório no release** (RNF25). Texto puro só no `debug`.
5. **Textos da interface em `strings.xml`**, em português. O `detail` dos erros já vem em português do backend e pode ser exibido direto.
6. **ViewModel sem `Context`.** Leitura do arquivo (`ContentResolver`) fica no repositório, injetado pelo Hilt.
7. Online-only: sem cache local no MVP. Sem rede, mostre o estado de erro com "tentar de novo".

---

## 9. Manter o contrato atualizado

A especificação (`fintrack-openapi.json`) é exportada do backend. O Swagger fica desligado em produção, então o arquivo do repositório é a referência do app. Para regenerar depois de mudar a API:

```bash
# backend rodando local (perfil dev)
curl -s -H "Authorization: Bearer dev-token-somente-local-nao-usar-em-producao" \
  http://localhost:8080/v3/api-docs | python -m json.tool --no-ensure-ascii > project/fintrack-openapi.json
```

Em desenvolvimento, a documentação navegável fica em `http://localhost:8080/swagger-ui.html` (botão **Authorize** com o token de dev).

Gerar o cliente com openapi-generator é opcional (arquitetura §8.3). A API é pequena, e escrever os DTOs à mão ajuda a aprender Kotlin; se gerar, revise o mapeamento de dinheiro, que precisa ser `BigDecimal`.

---

## 10. Pendências que afetam o app

| Item | Impacto | Onde |
|---|---|---|
| D-07 — domínio do servidor | Sem ele não há URL de release nem HTTPS | arquitetura §12 |
| D-08 — biblioteca de gráficos | Tela de relatórios | arquitetura §12 |
| 1.3 / Etapa 9 — Postgres e deploy | Até lá, o app só fala com o backend local | estado §8 |
| Busca por texto não ignora acentos | "farmacia" não acha "Farmácia" na listagem de transações (as **regras** ignoram acento; a busca não) | estado §6 |

---

## 11. Histórico de versões

| Versão | Data | Mudança |
|---|---|---|
| 0.1 | 2026-10-20 | Primeira versão, com o backend das Etapas 1–8. Exemplos capturados do backend real; especificação exportada para `fintrack-openapi.json`. |
