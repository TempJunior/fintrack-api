# Fintrack — Documento de Requisitos de Software (SRS)

| Campo | Valor |
|---|---|
| Projeto | Fintrack |
| Versão do documento | 0.3 |
| Status | Baseline v0.2 — revisado para cliente Android e deploy em AWS EC2 |
| Tipo | Projeto pessoal / estudos |
| Última atualização | 2026-10-20 |

---

## 1. Introdução

### 1.1 Objetivo do documento
Especificar **o que** o Fintrack deve fazer (requisitos funcionais) e **sob quais restrições** (requisitos não funcionais), antes de qualquer decisão de arquitetura ou tecnologia. Este documento é a linha de base (baseline) contra a qual o sistema será construído e verificado.

### 1.2 Objetivo do projeto
Construir um sistema pessoal de controle financeiro que importa extratos bancários em formato OFX, categoriza transações e gera relatórios, acessado por um app Android nativo. O projeto é também um veículo de estudo, com foco declarado em: lógica, Spring, Docker, arquitetura de sistemas, Android nativo (Kotlin), deploy em nuvem (AWS), resolução de problemas e documentação.

### 1.3 Escopo do MVP
O MVP (v0) cobre os **Módulos 1 a 6**:
1. Contas e Instituições
2. Importação de Extratos (OFX)
3. Transações
4. Categorização
5. Relatórios e Dashboards
6. Cliente Android

### 1.4 Fora de escopo (v0)
Registrado como Evolução Futura, deliberadamente adiado:
- Orçamento / metas por categoria com alertas de estouro
- Multiusuário e login com usuários reais (no MVP existe apenas uma credencial de acesso — ver P06)
- Modo offline no app (cache local / sincronização)
- Detecção automática de recorrências (assinaturas, contas fixas)
- Notificações
- Importação via CSV
- Exportação de relatórios (CSV/JSON)

### 1.5 Premissas e decisões-base
- **P01** — Sistema **single-user**. Não há cadastro de usuários, perfis ou multiusuário no MVP; o acesso é protegido por uma credencial única (P06).
- **P02** — Arquitetura é um **monólito** (ver ADR-001), organizado em camadas MVC (ADR-009, na arquitetura).
- **P03** — Moeda padrão BRL.
- **P04** — Entrada de dados é feita por **upload manual** de arquivos OFX, a partir do app Android. Não há conexão direta/automática com bancos no MVP (integração via Open Finance exigiria certificação regulatória, fora do escopo pessoal).
- **P05** — O dado que vem do banco é tratado como **fonte da verdade imutável**; qualquer enriquecimento (categoria, nota, tag) é armazenado separadamente.
- **P06** — O backend é hospedado em **AWS EC2 com acesso pela internet**. Por isso, autenticação (credencial única) e HTTPS são obrigatórios já no MVP.
- **P07** — A interface do MVP é um **app Android nativo**. Não há interface web ou desktop.

---

## 2. Glossário

| Termo | Definição |
|---|---|
| **OFX** | Open Financial Exchange. Formato-padrão de arquivo para troca de dados financeiros. Existe em v1 (baseado em SGML) e v2 (XML). É o formato de importação do MVP. |
| **FITID** | *Financial Institution Transaction ID*. Identificador único de uma transação atribuído pelo banco dentro de uma conta. Chave para deduplicação. |
| **Idempotência (da importação)** | Propriedade de que reprocessar o mesmo arquivo — total ou parcialmente — produz o mesmo resultado, sem duplicar transações. |
| **Transferência interna** | Movimentação de dinheiro entre duas contas do próprio usuário. Não deve ser contada como receita nem despesa nos relatórios. |
| **Dado bruto** | Os campos originais da transação exatamente como vieram do arquivo do banco. Imutável. |
| **Enriquecimento** | Informação adicionada pelo usuário ou por regras (categoria, nota, tag), armazenada separada do dado bruto. |
| **Regra de categorização** | Condição sobre a descrição da transação (contém / começa com / regex) que atribui automaticamente uma categoria. |
| **Cliente Android** | App nativo (Kotlin + Jetpack Compose) que consome a API REST do backend. É a única interface do MVP. |
| **Token de acesso** | Credencial única do usuário, enviada em toda requisição do app ao backend. |
| **RF / RNF** | Requisito Funcional / Requisito Não Funcional. |
| **ADR** | *Architecture Decision Record*. Documento curto que registra uma decisão de arquitetura e sua justificativa. |

---

## 3. Requisitos Funcionais

Legenda de escopo: **[MVP]** faz parte da v0 · **[Futuro]** registrado, fora da v0.

### Módulo 1 — Contas e Instituições
- **RF01** [MVP] — Permitir cadastrar uma conta financeira contendo: instituição (banco), tipo (corrente, poupança ou cartão de crédito), apelido e moeda (BRL padrão).
- **RF02** [MVP] — Permitir listar, editar e **inativar** contas. Contas não são excluídas, para preservar histórico.
- **RF03** [MVP] — Toda transação importada deve estar vinculada a exatamente uma conta.

### Módulo 2 — Importação de Extratos (OFX)
- **RF04** [MVP] — Permitir upload, a partir do app Android, de um arquivo OFX associado a uma conta.
- **RF05** [MVP] — Validar o arquivo antes de processar: formato (OFX v1 ou v2), encoding e estrutura mínima esperada.
- **RF06** [MVP] — Extrair de cada transação: data, valor, tipo (débito/crédito), descrição original e o identificador único (FITID).
- **RF07** [MVP] — Detectar e ignorar transações duplicadas usando **FITID + conta** como chave. Reimportar o mesmo arquivo ou períodos sobrepostos não pode gerar duplicatas.
- **RF08** [MVP] — Ao final de cada importação, registrar um resumo: total lido, novas inseridas, duplicadas ignoradas e erros.
- **RF09** [MVP] — Rejeitar arquivos inválidos com mensagem de erro clara, sem corromper dados existentes.
- **RF10** [Futuro] — Suportar importação via CSV como alternativa ao OFX.

### Módulo 3 — Transações
- **RF11** [MVP] — Persistir transações preservando o **dado bruto original** separado de qualquer enriquecimento. O dado do banco nunca é sobrescrito.
- **RF12** [MVP] — Listar transações com filtros combináveis: período, conta, categoria, tipo e texto na descrição.
- **RF13** [MVP] — Permitir enriquecer manualmente uma transação (categoria, nota, tag).
- **RF14** [MVP] — Permitir marcar uma transação como **transferência interna**, para que não seja contada como gasto/receita nos relatórios.

### Módulo 4 — Categorização
- **RF15** [MVP] — Permitir cadastrar categorias e subcategorias (ex.: Alimentação → Restaurante).
- **RF16** [MVP] — Permitir cadastrar regras de categorização baseadas na descrição (operadores: contém, começa com e, opcionalmente, regex).
- **RF17** [MVP] — Aplicar as regras automaticamente durante a importação.
- **RF18** [MVP] — Permitir sobrescrever manualmente a categoria; a escolha manual tem prioridade sobre as regras.
- **RF19** [MVP] — Permitir reaplicar regras em lote sobre transações já importadas.
- **RF20** [MVP] — Transações sem regra correspondente ficam como "Não categorizado".

### Módulo 5 — Relatórios e Dashboards
- **RF21** [MVP] — Total de receitas e despesas por período.
- **RF22** [MVP] — Gasto agrupado por categoria em um período.
- **RF23** [MVP] — Evolução mensal (série temporal de receitas vs. despesas).
- **RF24** [MVP] — Comparativo entre dois ou mais meses.
- **RF25** [MVP] — Saldo consolidado e saldo por conta.
- **RF26** [MVP] — Todos os relatórios filtráveis por conta, categoria e período.
- **RF27** [Futuro] — Exportar relatórios em CSV/JSON.

### Módulo 6 — Cliente Android
- **RF28** [MVP] — O app Android deve oferecer telas para todos os fluxos do MVP: contas, importação, transações, categorização e relatórios.
- **RF29** [MVP] — Permitir selecionar o arquivo OFX no armazenamento do dispositivo pelo seletor de arquivos do sistema.
- **RF30** [MVP] — Permitir configurar no app o endereço do servidor e a credencial de acesso.

---

## 4. Requisitos Não Funcionais

Legenda: **[MVP]** vale na v0 · **[Adiado]** aceito como pendente · **[Fase 2]** gancho futuro.

### Desempenho
- **RNF01** [Adiado] — Tempo máximo de importação por arquivo. *Alvo numérico a definir.*
- **RNF02** [Adiado] — Tempo máximo de resposta de relatórios. *Alvo numérico a definir.*
- **RNF03** [MVP] — Listagens devem usar paginação; nunca carregar todas as transações de uma vez. Vale para a API e para o app (rolagem paginada).

### Confiabilidade e Integridade dos Dados
- **RNF04** [MVP] — A importação deve ser **idempotente** (operacionaliza o RF07).
- **RNF05** [MVP] — A importação deve ser **atômica**: o arquivo é processado por completo ou nada é persistido. *(Decisão em aberto D-01: política para arquivos parcialmente inválidos.)*
- **RNF06** [MVP] — O dado bruto original de cada transação é imutável após a importação (reforça RF11).
- **RNF07** [MVP] — Deve existir mecanismo trivial de backup/recuperação (ex.: dados em volume persistente com dump periódico enviado para fora da instância EC2).

### Segurança (single-user com exposição pública)
- **RNF08** [MVP] — Nenhum dado sensível (número de conta, transações, valores) pode aparecer em logs — nem do backend, nem do app (Logcat). O token de acesso também nunca é logado.
- **RNF09** [MVP] — Configuração sensível (ex.: credenciais de banco, token de acesso) não fica hardcoded; vem de variáveis de ambiente ou config externa.
- **RNF10** [MVP] — O backend roda em uma instância AWS EC2 acessível pela internet (ADR-007). Apenas HTTPS fica exposto; banco de dados e porta da aplicação nunca são publicados, e SSH fica restrito ao IP do desenvolvedor.
- **RNF11** [MVP] — Toda requisição à API exige autenticação por credencial única (single-user), exceto o health check. *(Antecipado da Fase 2 por causa da exposição pública — ADR-008.)* A solução deve permitir evoluir para login real se o sistema virar multiusuário.
- **RNF25** [MVP] — Toda comunicação entre app e backend usa HTTPS.
- **RNF26** [MVP] — A credencial fica armazenada no dispositivo de forma segura (Android Keystore), nunca embutida no código do app.

### Observabilidade
- **RNF12** [MVP] — Toda importação gera registro de auditoria (quando, arquivo, conta, resultado — casa com RF08).
- **RNF13** [MVP] — Logs estruturados com níveis apropriados (INFO/WARN/ERROR).
- **RNF14** [MVP] — Endpoint de health check exposto.
- **RNF15** [Desejável] — Métricas básicas expostas (contadores de importação, tempo de processamento).

### Manutenibilidade e Qualidade
- **RNF16** [MVP] — Cobertura de testes automatizados com meta mínima (~70%) na lógica de negócio (parser, deduplicação, categorização, relatórios).
- **RNF17** [MVP] — A lógica de negócio deve ser testável **isolada** de framework e banco de dados.
- **RNF18** [MVP] — Estilo de código consistente, garantido por formatador/linter no build.

### Portabilidade e Operação
- **RNF19** [MVP] — Todo o backend (aplicação, banco e proxy reverso) sobe via containers com um único comando, localmente ou na EC2.
- **RNF20** [MVP] — No servidor, nenhuma dependência instalada manualmente além do runtime de containers. (O app Android é compilado à parte, com Android Studio/Gradle.)
- **RNF21** [MVP] — Configuração por ambiente (dev/prod) sem recompilar.

### Documentação
- **RNF22** [MVP] — API autodocumentada (OpenAPI/Swagger). A especificação é o contrato entre backend e app.
- **RNF23** [MVP] — README com setup + documento de arquitetura mantido junto ao código.
- **RNF24** [MVP] — Decisões arquiteturais relevantes registradas em ADRs.

---

## 5. Decisões em Aberto (log)

| ID | Assunto | Descrição | Status |
|---|---|---|---|
| **D-01** | Atomicidade parcial | Um arquivo com N linhas boas e algumas ruins: falha o arquivo inteiro (RNF05 estrito) ou importa as boas e reporta as ruins? | Resolvido p/ MVP — atômico (arquitetura §7.5) |
| **D-02** | Encoding de OFX BR | Bancos brasileiros costumam usar ISO-8859-1 em vez de UTF-8. Detectar pelo header ou assumir e converter? | Resolvido — ADR-004 (arquitetura) |
| **D-03** | Alvos de performance | Preencher RNF01 e RNF02 com números verificáveis. | Adiado |

---

## 6. Registro de Decisões de Arquitetura (ADRs)

### ADR-001 — Monólito modular em vez de microserviços

**Status:** Aceito — organização interna alterada pelo ADR-009 (camadas MVC em vez de módulos; ver arquitetura §10). Continua valendo a decisão de um único deployable.
**Data:** 2026-07-07

**Contexto.**
Um dos objetivos de estudo do projeto é praticar arquitetura de microserviços. Ao mesmo tempo, o domínio do Fintrack é pequeno, single-user, sem componentes que precisem escalar de forma independente e sem pressão organizacional (um único desenvolvedor).

**Decisão.**
Construir o sistema como um **monólito modular**: um único deployable, dividido internamente em módulos de fronteira bem definida (importação, transações, categorização, relatórios), com contratos explícitos entre eles.

**Consequências.**
- *Positivas:* menor custo operacional (sem rede entre serviços, sem consistência distribuída, deploy simples); permite praticar o **desenho** de fronteiras/bounded contexts, que é a parte conceitualmente difícil de microserviços; caminho recomendado pela indústria ("comece monólito, extraia quando doer").
- *Negativas:* não exercita, no MVP, o ferramental operacional de microserviços (comunicação entre serviços, mensageria, orquestração de múltiplos containers de aplicação).
- *Mitigação:* o objetivo de microserviços é atendido na Fase 2 como um **exercício de extração** — promover um módulo (candidato natural: importação) a serviço separado. As fronteiras internas do monólito são desenhadas desde já para tornar essa extração viável.

---

## 7. Histórico de Versões

| Versão | Data | Mudança |
|---|---|---|
| 0.1 | 2026-07-07 | Baseline inicial: escopo MVP (Módulos 1–5), single-user, monólito modular. RFs e RNFs congelados. ADR-001 registrado. |
| 0.2 | 2026-10-20 | Cliente passa a ser app Android nativo (Módulo 6, RF28–RF30, P07). Backend em AWS EC2 com acesso pela internet (P06): autenticação antecipada para o MVP (RNF11), RNF10 reescrito, RNF25–RNF26 adicionados. CSV confirmado fora do MVP. |
| 0.3 | 2026-10-20 | P02 e ADR-001 ajustados: monólito em camadas MVC, sem módulos internos (ADR-009). Requisitos inalterados. |
