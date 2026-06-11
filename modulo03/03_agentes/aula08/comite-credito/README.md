# Aula 08 (Voting): Comitê de Crédito Empresarial

> **Padrão**: Voting (council pattern)
> **Case**: Comitê de crédito PJ (5 analistas votam, maioria decide, relator redige o parecer)
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic (`@PlannerAgent` + `VotingPlanner`) · Ollama (`deepseek-v4-pro:cloud` + `deepseek-v4-flash:cloud`)

---

## O que você vai aprender

Nos padrões anteriores, cada agente fazia uma coisa **diferente**: o Orchestrator-Workers dividia o
trabalho em análises distintas, o Supervisor escolhia UM especialista, o Dynamic Routing trocava o modelo.
No padrão **Voting**, todos os agentes fazem **a mesma tarefa**, de forma totalmente independente, e a
resposta final **emerge por votação**.

É o padrão do mundo corporativo real: decisões críticas não dependem de uma pessoa só; passam por um
**comitê**. Cada membro olha o mesmo dossiê com uma lente própria (risco, comercial, compliance, garantias,
cenário setorial), vota sem ver o voto dos colegas, e o regimento define como os votos viram decisão.

No LangChain4j, o Voting não é uma annotation nova de workflow, e sim uma demonstração oficial do SPI
`Planner`: o `VotingPlanner` despacha todos os sub-agentes **em paralelo**, coleta a saída de cada um como
um voto e, quando o último voto chega, agrega tudo com uma `VotingStrategy`. Existem três estratégias
prontas (`majority()`, `average()`, `highest()`) e, como `VotingStrategy` é uma `@FunctionalInterface`
(`Object aggregate(Collection<Object> votes)`), qualquer lambda vira o "regimento" do seu comitê.

Quando usar: classificação e moderação de conteúdo, avaliação de risco, LLM-as-judge com júri, qualquer
decisão em que o consenso de N perspectivas independentes (prompts, personas ou até modelos diferentes)
reduz o viés e a alucinação pontual de um agente único.

```
                      @Inject ComiteAgent (entrypoint)
                                   │
                    @SequenceAgent (outputKey = "deliberacao")
                                   │
        ┌──────────────────────────┴──────────────────────────┐
        ▼                                                     ▼
ComiteDeCredito                                        RelatorComite
@PlannerAgent + @PlannerSupplier                       @Agent "relator"
(VotingPlanner → dispara os 5 EM PARALELO)             deepseek-v4-pro:cloud
        │                                              lê {resultadoComite} + {dossie}
  ┌─────┼─────────┬───────────┬───────────┐            escreve "parecer"
  ▼     ▼         ▼           ▼           ▼
Risco  Comercial  Compliance  Garantias   Setorial      ← 5× deepseek-v4-flash:cloud
"votoRisco" "votoComercial" "votoCompliance" "votoGarantias" "votoSetorial"
  └─────┴─────────┴───────────┴───────────┘
                  │  Collection<Object> com 5 Voto
                  ▼
   VotingStrategy custom (ComiteDeCredito::consolidar):
   maioria simples + desempate pela decisão mais conservadora
                  │
                  ▼
        "resultadoComite" (decisão final, placar, 5 votos)
```

## Como rodar

Pré-requisito: Ollama acessível em `localhost:11434` (Ollama Cloud) com os modelos
`deepseek-v4-pro:cloud` e `deepseek-v4-flash:cloud` disponíveis.

```bash
cd modulo03/03_agentes/aula08/comite-credito
./mvnw quarkus:dev
```

Abra <http://localhost:8080/>. Use "↳ usar dossiê de exemplo" para gerar um dossiê sintético, clique em
"Submeter ao Comitê" e acompanhe: os 5 cards votam ao mesmo tempo, o placar aparece com a decisão final e
o relator redige o parecer.

## Estrutura do código

```
comite-credito/
├── pom.xml                                  # deps padrão do módulo + langchain4j-agentic-patterns (VotingPlanner/VotingStrategy)
├── src/main/resources/
│   ├── application.properties               # default deepseek-v4-pro:cloud + "smaller" deepseek-v4-flash:cloud
│   └── META-INF/resources/index.html        # mesa do comitê: 5 cards de voto, placar e parecer
└── src/main/java/com/eldermoraes/
    ├── ComiteWebsocket.java                 # @WebSocket /ws/comite; @OnTextMessage retorna Multi<ComiteEvent>
    ├── ai/
    │   ├── AnalistaRisco.java               # vota sob a lente de capacidade de pagamento
    │   ├── AnalistaComercial.java           # vota sob a lente de relacionamento e potencial de negócio
    │   ├── AnalistaCompliance.java          # vota sob a lente de restrições cadastrais e regulatório
    │   ├── AnalistaGarantias.java           # vota sob a lente de garantias e recuperação de crédito
    │   ├── AnalistaSetorial.java            # vota sob a lente setorial/macroeconômica
    │   ├── RelatorComite.java               # redige o parecer formal (modelo default, robusto)
    │   └── ExampleGenerator.java            # gera dossiês de crédito sintéticos para a demo
    ├── workflow/
    │   ├── ComiteDeCredito.java             # @PlannerAgent + @PlannerSupplier + VotingStrategy custom (o coração da aula)
    │   ├── ComiteAgent.java                 # @SequenceAgent (comitê → relator) + @Output
    │   └── ComiteOrchestrator.java          # wrapper Multi + virtual thread para o WebSocket
    ├── rest/ExampleResource.java            # GET /api/example/dossie (@RunOnVirtualThread)
    └── dto/                                 # Voto, DecisaoVoto, ResultadoComite, Deliberacao, DossieInput, ComiteEvent
```

### Pontos-chave

#### 1. Cinco analistas, o MESMO input, personas diferentes

Todos os analistas recebem exatamente o mesmo `{dossie}` (o `@UserMessage` é idêntico nos 5). A
independência dos votos vem dos `@SystemMessage`: cada um define uma persona com lente exclusiva e a
instrução explícita de NÃO invadir o papel dos colegas:

```java
@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface AnalistaRisco {

    @SystemMessage("""
            Você é o analista de risco de crédito de um banco brasileiro, com 15 anos de experiência em
            crédito para pessoa jurídica. Você é membro votante do comitê de crédito.
            ...
            Seu perfil é técnico e conservador: na dúvida sobre a capacidade de pagamento, você vota
            APROVAR_COM_RESSALVAS (e diz qual ressalva) ou NEGAR. Você NÃO considera potencial comercial
            nem simpatia pelo cliente; isso é papel de outro analista.
            ...
            """)
    @UserMessage("""
            DOSSIÊ DE CRÉDITO:
            {dossie}
            """)
    @Agent(name = "risco",
            description = "Analista de risco de crédito: avalia capacidade de pagamento e endividamento",
            outputKey = "votoRisco")
    @ModelName("smaller")
    Voto votar(@V("dossie") String dossie);
}
```

Atenção à dupla de annotations de modelo: o `modelName = "smaller"` do `@RegisterAiService` vale quando
a interface é usada como AI service comum (injetada diretamente com `@Inject`); já o `@ModelName("smaller")`
**no método** `@Agent` é o que a composição lê: dentro do comitê, o bean é construído pela extensão
agentic, e é essa annotation no método que amarra o agente ao modelo nomeado da configuração
(`quarkus.langchain4j.ollama.smaller.*`). Por isso as duas andam juntas e alinhadas. Cada voto custa
pouco: 5× `deepseek-v4-flash:cloud` em paralelo; é o consenso que dá robustez, não o tamanho de cada
votante.

#### 2. `@PlannerAgent` + `@PlannerSupplier`: plugando o `VotingPlanner`

A composição de votação não usa `@ParallelAgent` nem `@SequenceAgent`: ela pluga um **planner**
programático via `@PlannerAgent`, e o método estático `@PlannerSupplier` entrega a implementação:

```java
public interface ComiteDeCredito {

    @PlannerAgent(name = "comiteCredito",
            description = "Comitê de crédito: 5 analistas independentes votam sobre o mesmo dossiê",
            outputKey = "resultadoComite",
            subAgents = {
                    AnalistaRisco.class,
                    AnalistaComercial.class,
                    AnalistaCompliance.class,
                    AnalistaGarantias.class,
                    AnalistaSetorial.class
            })
    ResultadoComite deliberar(@V("dossie") String dossie);

    @PlannerSupplier
    static Planner planner() {
        return new VotingPlanner(ComiteDeCredito::consolidar);
    }
}
```

Detalhe importante: o framework chama o `Supplier` **a cada deliberação**. O `VotingPlanner` é stateful
(acumula os votos que vão chegando), então cada execução precisa de uma instância nova; é por isso que a
API pede um supplier, e não uma instância.

#### 3. `VotingStrategy`: o regimento do comitê como código

`VotingStrategy` é uma `@FunctionalInterface` com um único método: `Object aggregate(Collection<Object> votes)`.
As estratégias prontas cobrem os casos clássicos: `majority()` para rótulos categóricos, `average()` para
notas numéricas, `highest()` para "a maior avaliação vence". Aqui o regimento exige mais: maioria simples
**com desempate pela decisão mais conservadora**, então a estratégia é uma lambda nossa:

```java
static ResultadoComite consolidar(Collection<Object> votes) {
    List<Voto> votos = votes.stream().map(Voto.class::cast).toList();

    Map<DecisaoVoto, Long> contagem = votos.stream()
            .collect(Collectors.groupingBy(Voto::decisao, Collectors.counting()));

    long maisVotada = contagem.values().stream().max(Long::compare).orElse(0L);

    DecisaoVoto decisaoFinal = contagem.entrySet().stream()
            .filter(e -> e.getValue() == maisVotada)
            .map(Map.Entry::getKey)
            .max(Comparator.comparingInt(Enum::ordinal))   // desempate: a mais conservadora
            .orElse(DecisaoVoto.NEGAR);
    // ... monta o placar e devolve ResultadoComite(decisaoFinal, placar, votos)
}
```

O desempate funciona porque a **ordem dos constantes do enum codifica a severidade**
(`APROVAR < APROVAR_COM_RESSALVAS < NEGAR`): num empate 2×2×1, vence a decisão de maior `ordinal()`.
Repare também que a estratégia recebe os **records `Voto` completos**, não strings: a agregação devolve um
`ResultadoComite` rico, com placar e justificativas, pronto para o relator e para a UI.

#### 4. O Voting compõe com o que você já conhece: `@SequenceAgent` + `@Output`

O agente votador entra como sub-agente de um `@SequenceAgent`, exatamente como o `@ParallelAgent` entrou
na aula02; patterns são peças combináveis:

```java
public interface ComiteAgent {

    @SequenceAgent(outputKey = "deliberacao",
            subAgents = { ComiteDeCredito.class, RelatorComite.class })
    Deliberacao deliberar(@V("dossie") String dossie);

    @Output
    static Deliberacao assemble(AgenticScope scope) {
        return new Deliberacao(
                (ResultadoComite) scope.readState("resultadoComite"),
                (String) scope.readState("parecer"));
    }
}
```

O `RelatorComite` (modelo default, robusto) lê `{resultadoComite}` no prompt. Como template de prompt
interpola objetos via `toString()`, o record `ResultadoComite` sobrescreve `toString()` para entregar ao
relator um texto legível com decisão, placar e os 5 votos; sem isso, o relator receberia a serialização
crua do record.

#### 5. `ComiteOrchestrator`: wrapper Multi para WebSocket

Forma padrão do módulo: o orchestrator embrulha a chamada (bloqueante) do agente composto numa virtual
thread e emite eventos para o WebSocket (`RECEBIDO` ao receber o dossiê, `VOTACAO` com o resultado da
agregação e `PARECER` com o texto do relator):

```java
public Multi<ComiteEvent> deliberarAsStream(String dossie) {
    return Multi.createFrom().emitter(emitter ->
            Thread.startVirtualThread(() -> runDeliberacao(dossie, emitter)));
}
```

## Por dentro do pattern: o SPI `Planner`

O Voting revela o motor que existe por baixo de TODAS as composições declarativas que você usou no módulo.
Um `Planner` decide, passo a passo, quais agentes invocar:

- `firstAction(ctx)`: primeira jogada; o `VotingPlanner` retorna `call(subagents)`, despachando os 5
  analistas **de uma vez**;
- `nextAction(ctx)`: chamado a cada agente que completa; o planner coleta
  `previousAgentInvocation().output()` como voto e responde `noOp()` enquanto faltarem votos. No 5º voto,
  agrega com a `VotingStrategy` e retorna `done(resultado)`;
- `topology()`: retorna `PARALLEL`, informando ao runtime que os sub-agentes executam simultaneamente.

E onde esses 5 votos rodam? Em **virtual threads**: o executor default do framework agentic no Java 21+
é `Executors.newVirtualThreadPerTaskExecutor()`, então o paralelismo não consome (nem exige dimensionar)
nenhum pool de threads da aplicação. Se precisar de outro comportamento, o método estático
`@ParallelExecutor` na interface da composição permite fornecer um executor próprio.

`VotingPlanner` e `VotingStrategy` moram no artefato `dev.langchain4j:langchain4j-agentic-patterns`
(a única dependência que esta aula adiciona ao `pom.xml` padrão do módulo). A mensagem importante: os
workflows declarativos (`@SequenceAgent`, `@ParallelAgent`, `@SupervisorAgent`...) são planners prontos;
e com o SPI você pode criar **qualquer topologia** própria e plugá-la com as mesmas duas annotations
(`@PlannerAgent` + `@PlannerSupplier`).

## O que observar

| Observação | Explica… |
|---|---|
| Nos logs, 5 requisições ao `deepseek-v4-flash:cloud` disparam no MESMO instante | `VotingPlanner.firstAction()` retorna `call(subagents)` com topologia `PARALLEL`; os votos não são sequenciais |
| Os 5 votos saem no flash e só o parecer sai no `deepseek-v4-pro:cloud` | `@ModelName("smaller")` no método `@Agent` dos analistas; o `RelatorComite` fica no modelo default |
| A linha `>> decisao=… placar=… votos=5` aparece UMA vez por deliberação | `consolidar()` roda uma única vez, quando o último voto chega; não há "rodadas" de discussão |
| Dossiês intermediários dividem o placar (4×1, 3×2) e os bons/ruins saem unânimes | as personas têm lentes conflitantes: compliance vê a restrição, comercial vê o relacionamento |
| Empate no placar resolve para a decisão mais conservadora | `Comparator.comparingInt(Enum::ordinal)` sobre a ordem de severidade do enum `DecisaoVoto` |
| O parecer cita os analistas pelo nome da função e respeita o placar | o relator recebe `{resultadoComite}` interpolado via `toString()` do record; votos e placar viram texto no prompt |
| O card "Parecer do Relator" mostra spinner depois que o placar já apareceu | o `@SequenceAgent` segue do comitê para o relator: a votação termina antes de a redação começar |

## Para experimentar

- Troque a estratégia custom por `VotingStrategy.majority()` fazendo os analistas retornarem só
  `DecisaoVoto`, e veja o que se perde (justificativas, placar, desempate).
- Dê peso 2 ao voto do Compliance: na lambda, conte o voto dele em dobro antes de apurar a maioria.
- Implemente regra de veto (quórum qualificado): qualquer voto NEGAR derruba a operação, independentemente
  da maioria.
- Adicione um 6º analista (ex.: jurídico) e observe o efeito do número par de votantes no desempate
  conservador.
- Monte um council heterogêneo: deixe cada analista num MODELO diferente (basta variar o `@ModelName` de
  cada método) e compare a qualidade da deliberação.
