# Aula 07 — Agent-to-Agent (A2A): Negociação Comprador × Vendedor

> **Padrão**: Agent-to-Agent (A2A)
> **Case**: Negociação B2B autônoma entre dois agentes em **apps separados** (portas distintas)
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic · Ollama (`gpt-oss:120b-cloud`) · Maven multi-módulo

---

## O que você vai aprender

Este é o **único projeto multi-módulo** do curso. Demonstra o padrão **A2A** (Agent-to-Agent): dois agentes em **processos Quarkus distintos**, em **portas distintas**, conversando via **HTTP** até chegarem a um acordo (ou impasse).

```
   ┌────────────────────────────────┐                 ┌─────────────────────────────────┐
   │ comprador-app (porta 8080)     │                 │ vendedor-app (porta 8081)       │
   │                                │                 │                                 │
   │  Frontend (WS /ws/negociacao)  │                 │  Frontend (WS /ws/dashboard)    │
   │            ↓                   │                 │            ↑                    │
   │  NegociacaoCoordinator         │                 │  VendedorDashboard (broadcast)  │
   │            ↓                   │                 │            ↑                    │
   │  CompradorAgent (LLM)          │   loop até 5    │  VendedorAgent (LLM)            │
   │     decide ACEITAR/CONTRAPOR/  │   rodadas       │     respeita preço mínimo       │
   │     DESISTIR                   │                 │     do catálogo                 │
   │            ↓                   │                 │            ↑                    │
   │  VendedorRemote (@REST Client) │ ──POST JSON──▶  │  NegociacaoEndpoint (@POST)     │
   │                                │ ◀──RespostaVendedor─                              │
   └────────────────────────────────┘                 └─────────────────────────────────┘
        localhost:8080                                     localhost:8081
```

### Por que este padrão importa

- **Desacoplamento total**: cada agente vive num app distinto — diferente owner, deploy, ciclo de vida, time, até **empresa**
- **Permite composições B2B**: sua aplicação fala com um agente de outra empresa via protocolo estável
- **Cada lado mantém seus segredos**: comprador não vê margem mínima; vendedor não vê orçamento; eles só trocam mensagens de negociação

### Casos de uso reais

- **Compras corporativas automatizadas** (este projeto: comprador interno × vendedor externo)
- **Suporte N1 → N2 interorganizacional** (cliente final → parceiro técnico)
- **Marketplace agent-to-agent** (procurement agent escolhendo entre N vendedores)
- **Reserva de viagem** (agente do cliente conversa com agente da cia aérea/hotel)

---

## Sobre o protocolo A2A

A Google (agora sob Linux Foundation) propôs o **A2A Protocol** como padrão para comunicação entre agentes — JSON-RPC 2.0 sobre HTTP + agent cards em `/.well-known/agent-card.json`. Existem SDKs Java (`io.github.a2asdk:a2a-java-sdk-server-quarkus`) e o módulo `dev.langchain4j:langchain4j-agentic-a2a` (apenas client) — mas ambos ainda são **experimentais (Beta1)** e podem ter incompatibilidades.

Este projeto usa **REST direto** (`@RegisterRestClient` + endpoint `@POST`) — o **padrão arquitetural é idêntico** (dois processos, mensagens síncronas, JSON sobre HTTP), só sem o envelope JSON-RPC. Quando o SDK A2A estabilizar, basta trocar `VendedorRemote` (uma classe) pelo cliente A2A; o `NegociacaoCoordinator` e o restante ficam intactos.

---

## Como rodar

Você precisa **2 terminais** (um por app):

### Terminal 1 — Vendedor (sobe primeiro, porta 8081)

```bash
cd modulo03/03_agentes/aula07/a2a-negociacao/vendedor-app
./mvnw quarkus:dev
```

Aguarde mensagem "Listening on: http://localhost:8081".

### Terminal 2 — Comprador (porta 8080)

```bash
cd modulo03/03_agentes/aula07/a2a-negociacao/comprador-app
./mvnw quarkus:dev
```

Aguarde "Listening on: http://localhost:8080".

### Abra **duas abas** no navegador

- <http://localhost:8080/> → frontend do **Comprador** (inicia negociação)
- <http://localhost:8081/> → dashboard read-only do **Vendedor** (vê tudo chegando)

---

## Como usar a UI

### Aba Comprador (localhost:8080)

1. Preencha: produto, orçamento máximo, prazo, critérios
2. Ou clique num exemplo (servidor, storage, licença, switch)
3. Clique em **Iniciar Negociação A2A**
4. Veja as **rodadas** se sucedendo:
   - 🏪 Vendedor responde via A2A com preço/prazo/condições
   - 🛒 Comprador decide ACEITAR / CONTRAPOR / DESISTIR
   - Loop até 5 rodadas (configurável em `application.properties`)
5. **Resultado final**: ✅ Acordo (com preço) ou ❌ Impasse

### Aba Vendedor (localhost:8081)

- **Catálogo** com preço de tabela e preço mínimo (transparente didaticamente — em produção ficaria oculto)
- **Rodadas recebidas**: cada vez que o comprador envia algo, aparece um card mostrando o que chegou e o que foi respondido
- Atualização em tempo real via WebSocket

---

## Estrutura multi-módulo

```
aula07/a2a-negociacao/
├── pom.xml                          — pai (packaging=pom, dois <module>)
├── README.md                        — este arquivo
├── comprador-app/
│   ├── pom.xml                      — herda do pai
│   ├── mvnw, mvnw.cmd, .mvn/
│   └── src/main/
│       ├── java/com/eldermoraes/comprador/
│       │   ├── ai/CompradorAgent.java
│       │   ├── negociacao/
│       │   │   ├── VendedorRemote.java          — @RegisterRestClient
│       │   │   └── NegociacaoCoordinator.java   — loop de até 5 rodadas
│       │   ├── ws/CompradorWebSocket.java
│       │   └── dto/                              — records
│       └── resources/
│           ├── application.properties           — porta 8080 + URL vendedor
│           └── META-INF/resources/index.html
└── vendedor-app/
    ├── pom.xml                      — herda do pai
    ├── mvnw, mvnw.cmd, .mvn/
    └── src/main/
        ├── java/com/eldermoraes/vendedor/
        │   ├── ai/VendedorAgent.java
        │   ├── catalogo/
        │   │   ├── Produto.java
        │   │   └── CatalogoService.java
        │   ├── rest/NegociacaoEndpoint.java     — @POST /a2a/negociacao
        │   ├── ws/VendedorDashboard.java        — broadcast WS
        │   └── dto/                              — records
        └── resources/
            ├── application.properties           — porta 8081
            └── META-INF/resources/index.html
```

### Pontos-chave para os alunos

#### 1. POM multi-módulo

O `pom.xml` raiz tem `packaging=pom` e `<modules>` listando os 2 apps. Centraliza versões (Quarkus, Java, plugins) — cada filho herda via `<parent>`.

```xml
<!-- pom.xml raiz -->
<modules>
    <module>comprador-app</module>
    <module>vendedor-app</module>
</modules>
```

```xml
<!-- comprador-app/pom.xml -->
<parent>
    <groupId>com.eldermoraes</groupId>
    <artifactId>a2a-negociacao-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>
<artifactId>comprador-app</artifactId>
```

#### 2. REST Client do lado comprador

```java
@RegisterRestClient(configKey = "vendedor")
@Path("/a2a/negociacao")
public interface VendedorRemote {
    @POST
    RespostaVendedor negociar(MensagemNegociacao mensagem);
}
```

`configKey="vendedor"` mapeia para `quarkus.rest-client.vendedor.url=http://localhost:8081` no `application.properties`.

#### 3. NegociacaoCoordinator — o loop A2A

O coração do padrão A2A. Em até 5 rodadas:
1. Envia mensagem ao vendedor via HTTP
2. Recebe `RespostaVendedor`
3. Pergunta ao `CompradorAgent` (LLM) o que fazer
4. Se ACEITAR → fecha; se DESISTIR → desiste; se CONTRAPOR → próxima rodada

```java
for (int rodada = 1; rodada <= maxRodadas; rodada++) {
    RespostaVendedor respostaVendedor = vendedorRemoto.negociar(msg);
    DecisaoComprador decisao = comprador.avaliar(...);
    if (decisao.acao() == ACEITAR) return acordo;
    if (decisao.acao() == DESISTIR) return impasse;
    mensagemAtual = decisao.mensagem();
}
```

#### 4. Cada agente tem segredos

- **Comprador** sabe: orçamento máximo, prazo, critérios → **NÃO** envia ao vendedor
- **Vendedor** sabe: preço de tabela, preço mínimo, prazo padrão → **NÃO** envia ao comprador
- O que viaja é apenas a **mensagem de negociação** + número da rodada + último valor proposto

Esse encapsulamento é central no padrão A2A — em produção real, cada agente roda em servidor próprio, com seu próprio `gpt-oss`, seus próprios prompts, sua própria base de conhecimento.

---

## O que observar nos dois frontends

### No comprador (localhost:8080)

| Observação | Explica… |
|---|---|
| Rodadas aparecem em sequência (uma a uma) | Loop síncrono — espera resposta antes da próxima |
| Vendedor cede gradualmente | Estratégia do prompt do `VendedorAgent` |
| Tag "LIMITE VENDEDOR" quando vendedor não pode mais ceder | `RespostaVendedor.limiteAtingido` |
| Resultado final ✅/❌ | Loop termina em ACEITAR, DESISTIR ou estouro de rodadas |

### No vendedor (localhost:8081)

| Observação | Explica… |
|---|---|
| Cada rodada aparece **em tempo real** | Broadcast WS |
| Vê **catálogo com preço mínimo** que o comprador não vê | Encapsulamento de segredos |
| `compradorId` aleatório por negociação | Identifica a sessão sem expor dados do cliente |

---

## Para experimentar

- **Mude o `maxRodadas`** para 3 ou 8 e veja como muda a dinâmica
- **Aumente o `precoMinimo`** de um produto no catálogo até gerar impasse
- **Coloque orçamento absurdamente baixo** (R$ 100 para servidor) — comprador desiste rápido
- **Mude `temperature`** dos agentes — temperaturas mais altas geram negociações mais "criativas"
- **Adicione critérios mais elaborados** ("desconto se pagamento à vista") e veja se os agentes entendem
- **Versão real A2A**: troque `VendedorRemote` por `AgenticServices.a2aBuilder("http://localhost:8081")` (precisa adicionar `langchain4j-agentic-a2a` 1.10.x ao comprador e `a2a-java-sdk-server-quarkus` 0.2.3.Beta1 ao vendedor)

---

## Próxima aula

Este é o **último projeto** do módulo de Agentes. Os 6 padrões cobertos:

| Aula | Padrão | Pasta |
|------|--------|-------|
| 02 | Orchestrator-Workers | `aula02/triagem-curricular` |
| 03 | Parallel | `aula03/traducao-paralela` |
| 04 | Supervisor | `aula04/triagem-medica` |
| 05 | Dynamic Routing | `aula05/ticket-router` |
| 06 | Human-in-the-Loop | `aula06/aprovacao-desconto` |
| 07 | Agent-to-Agent | `aula07/a2a-negociacao` *(este)* |

Cada um demonstra uma **forma diferente de compor agentes** em produção. Em sistemas reais, você vai combinar 2-3 destes padrões para resolver um problema.
