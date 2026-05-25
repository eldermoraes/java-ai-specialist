# Aula 06 — Human-in-the-Loop (HITL): Aprovação de Desconto B2B

> **Padrão**: Human-in-the-Loop
> **Case**: Aprovação de desconto comercial — agente prepara, gerente decide, agente responde
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic · Ollama (`gpt-oss:120b-cloud`) · Postgres via Dev Services

---

## O que você vai aprender

Este projeto demonstra o padrão **Human-in-the-Loop** (HITL): um agente automatiza parte do fluxo, **pausa** num ponto que exige decisão humana, e **retoma** depois que o humano decide. Diferente de "humano olhando logs depois", aqui o humano é parte do fluxo síncrono.

```
   ┌─────────┐  pedido  ┌──────────────┐
   │Vendedor │─────────▶│ Comercial    │ analisa e propõe %
   └─────────┘          │ Agent (LLM)  │
        ▲               └──────┬───────┘
        │                      │
        │                      ▼ proposta persistida em PG
        │              ┌─────────────────┐
        │              │ Approval        │   ⏸  PAUSA aqui
        │              │ Service         │   waiter em CompletableFuture
        │              └────────┬────────┘
        │                       │ broadcast WS
        │                       ▼
        │              ┌─────────────────┐
        │              │   Gerente       │   ✅ Aprovar
        │              │   (humano)      │   ❌ Rejeitar
        │              └────────┬────────┘   ↻ Contrapor (novo %)
        │                       │
        │                       ▼ future.complete(decisao)
        │              ┌─────────────────┐
        │              │ RespostaFinal   │ formata resposta ao vendedor
        │              │ Agent (LLM)     │
        │              └────────┬────────┘
        └───────────────────────┘
                resposta final
```

### Por que este padrão importa

- **Decisões irreversíveis com IA** (pagamentos, descontos, aprovações regulatórias) **não devem** ser tomadas pelo LLM sozinho — risco/custo de erro alto
- **HITL preserva auditabilidade**: cada proposta fica persistida com a decisão humana e observação
- **Mantém o humano como árbitro**: o agente faz a parte chata (analisar contexto, calcular %), o humano decide e justifica

### Casos de uso reais

- **Aprovação de desconto B2B** (este projeto)
- **Pagamentos acima de limite** (compliance financeiro)
- **Publicação de comunicados regulatórios** (compliance officer revisa)
- **Aprovação de cláusulas contratuais** sugeridas por agente

---

## Decisão técnica: por que NÃO usei `@HumanInTheLoop`

O `langchain4j-agentic` tem uma anotação `@HumanInTheLoop`, mas:
1. Ela espera output binário formatado pela LLM — nossa decisão é **ternária** (aprovar/rejeitar/contrapor com `%`)
2. Não há persistência built-in — perda em restart
3. Maintainers do LangChain4j classificam a implementação atual como "naive" ([issue #3405](https://github.com/langchain4j/langchain4j/issues/3405))

Usamos o **padrão programático do workshop Quarkus step 5**:
- `ApprovalService` `@ApplicationScoped` mantém `ConcurrentMap<Long, CompletableFuture<ApprovalDecision>>`
- Workflow bloqueia em `future.get(timeout)` numa worker thread
- Decisão do gerente chega via WS e completa o future
- Tudo persistido em Postgres para sobreviver a restart

---

## Como rodar

Pré-requisitos:
- Docker rodando (Quarkus Dev Services sobe Postgres automaticamente)
- Ollama Cloud configurado (`OLLAMA_HOST` apontando para serviço com `gpt-oss:120b-cloud`)

```bash
cd modulo03/03_agentes/aula06/aprovacao-desconto
./mvnw quarkus:dev
```

Abra: <http://localhost:8080/>

---

## Como usar a UI

A tela mostra **2 painéis lado a lado** simulando os 2 papéis numa só janela. Em produção seriam 2 perfis/usuários distintos — você pode abrir em 2 abas se quiser separação.

### Painel esquerdo: 👤 Vendedor

1. Cole/escreva um pedido (ou clique num dos exemplos: pequeno/médio/grande)
2. Clique em **Enviar Pedido**
3. Acompanhe a timeline:
   - 📨 "Pedido recebido. Analisando…"
   - 📋 "Proposta #N preparada: X% — enviada para aprovação"
   - ⏳ aguardando…
   - ✅ "Decisão recebida: APROVADA/REJEITADA/CONTRAPROPOSTA"
   - 📝 resposta final formatada do agente

### Painel direito: 🏢 Gerente Comercial

1. Ao abrir, recebe **snapshot** de todas as propostas (incluindo pendentes de execuções anteriores — recuperação pós-restart)
2. Quando uma proposta nova chega, aparece com badge **PENDENTE** amarelo
3. 3 opções:
   - **✓ Aprovar** — aceita o % proposto pelo agente
   - **✕ Rejeitar** — recusa a venda nestas condições
   - **↻ Contrapor** — abre input para novo % + observação
4. Decisão é enviada via WS, atualiza estado da proposta, libera o vendedor

---

## Estrutura do código

```
src/main/java/com/eldermoraes/
├── ai/
│   ├── ComercialAgent.java         — analisa pedido e propõe % de desconto
│   └── RespostaFinalAgent.java     — formata resposta final ao vendedor
├── hitl/
│   ├── ApprovalProposal.java       — Panache entity persistida em Postgres
│   ├── ApprovalStatus.java         — enum (PENDENTE/APROVADA/REJEITADA/CONTRAPROPOSTA/EXPIRADA)
│   └── ApprovalService.java        — gerencia futures + persistência
├── workflow/
│   └── DescontoWorkflow.java       — orquestra: agente → aguardar → agente
├── ws/
│   ├── VendedorWebSocket.java      — /ws/vendedor/{id}
│   ├── GerenteWebSocket.java       — /ws/gerente
│   ├── VendedorRegistry.java       — Map<vendedorId, connection>
│   └── GerenteRegistry.java        — Set<connection> para broadcast
└── dto/                             — records de eventos e DTOs
```

### Pontos-chave para os alunos

#### 1. Bloqueio com `CompletableFuture.get(timeout)`

O coração do HITL:

```java
public ApprovalDecision aguardarDecisao(Long propostaId) {
    CompletableFuture<ApprovalDecision> future = waiters.get(propostaId);
    return future.get(timeoutMinutes, TimeUnit.MINUTES);  // bloqueia thread
}
```

A worker thread fica parada em `.get()` por até 10 minutos. Isso é **aceitável aqui** porque:
- Volume é baixo (decisão humana, não pico de tráfego)
- Quarkus tem worker pool generoso
- Vendedor está esperando interativamente

Em produção com volumes maiores, considerar transformar em **state machine assíncrona** (event sourcing).

#### 2. Persistência sobrevive a restart

A `ApprovalProposal` é persistida em Postgres antes do `.get()`. Se o servidor reiniciar:
- O gerente reconecta → vê snapshot das pendentes
- Quando decidir, a entity é atualizada, mas o `CompletableFuture` original já se perdeu
- **Tratamento idempotente**: `ApprovalService.decidir()` não falha se não houver waiter — apenas atualiza a entity e broadcasta para os gerentes

Para fechar o loop com o vendedor após restart, ele precisa reconectar com o mesmo `vendedorId` — o `VendedorRegistry` o reconhece e o frontend pode enviar `GET /pendentes/{vendedorId}` (TODO didático).

#### 3. Dois WebSockets, registros separados

- `/ws/vendedor/{vendedorId}` — path param identifica o vendedor; `VendedorRegistry` mantém `Map<String, Connection>`
- `/ws/gerente` — sem identificação, mas há **broadcast**; `GerenteRegistry` mantém `Set<Connection>`

Permite múltiplos gerentes vendo a mesma fila (todos veem nova proposta, primeiro que decide ganha).

#### 4. Postgres Dev Services

Não precisa instalar PG. `quarkus.devservices.enabled=true` + Docker = PG sobe na primeira execução, é descartado ao parar. Configuração explícita no `application.properties`:

```properties
quarkus.datasource.db-kind=postgresql
quarkus.hibernate-orm.database.generation=drop-and-create
```

---

## O que observar no frontend

| Observação | Explica… |
|---|---|
| Vendedor envia, agente propõe rápido, depois "aguardando…" | Pausa do workflow |
| Gerente vê proposta nova aparecer em tempo real | Broadcast WS |
| Gerente decide → vendedor recebe **resposta final reformulada** | RespostaFinalAgent reformula |
| Status do card no gerente muda de PENDENTE → APROVADA/etc. | Persistência + atualização |
| Se reiniciar o servidor com PENDENTE no DB, gerente reconecta e vê | Snapshot pós-restart |

---

## Para experimentar

- **Teste o timeout**: configure `hitl.approval.timeout.minutes=1` e veja o vendedor receber erro de timeout
- **Adicione validação de % máximo**: gerente não pode aprovar mais que 25% mesmo via contraproposta
- **Adicione perfis de gerente**: nível 1 aprova até 15%, nível 2 até 30% (auth via header)
- **Audit trail completo**: além de status, persista o histórico de quem decidiu, em qual horário, em que IP
- **Email de notificação**: se gerente não está conectado, dispare email com link de aprovação
- **Versão assíncrona**: substitua `CompletableFuture.get()` por callback REST que retoma o workflow (sem bloquear thread)

---

## Próxima aula

Aula 07: **Agent-to-Agent (A2A)** — dois apps Quarkus em portas diferentes se comunicam via protocolo A2A do Google. Comprador negocia com vendedor até acordo ou impasse.
