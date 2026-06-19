# Aula 10 (Desafio): Combine os Padrões

> **Formato**: desafio prático (proposta de projeto, sem código de referência)
> **Objetivo**: projetar e implementar uma solução que combine no mínimo dois dos sete padrões do módulo
> **Stack base**: Quarkus 3.35.2 · Java 25 · LangChain4j Agentic · Ollama (a mesma das aulas 02 a 09)

---

## O que este desafio pede

Ao longo do módulo você viu um padrão de cada vez: Orchestrator-Workers na triagem de currículos, Supervisor na triagem médica, Voting no comitê de crédito, e assim por diante. Cada aula isolou um padrão para deixar o mecanismo claro.

Sistemas reais quase nunca usam um padrão puro. A própria aula 09 fecha com essa ideia: uma war-room pode ter um Blackboard cujos especialistas são supervisores, com um humano aprovando no meio do fluxo. Os padrões são peças que se encaixam.

Este desafio pede exatamente isso: você vai escolher um problema e resolvê-lo combinando no mínimo dois dos sete padrões numa única solução.

## Os sete padrões na sua caixa de ferramentas

Antes de escolher, relembre o que cada padrão resolve e qual a ferramenta declarativa que o entrega:

| Padrão | Resolve | Ferramenta no LangChain4j |
|---|---|---|
| Orchestrator-Workers | decompor uma tarefa em análises distintas e sintetizar | `@ParallelAgent` + `@SequenceAgent` + `@Output` |
| Parallel | aplicar o mesmo agente a N entradas ao mesmo tempo | `@ParallelMapperAgent` |
| Supervisor | deixar um planner LLM rotear para o especialista certo | `@SupervisorAgent` + `@SupervisorRequest` |
| Dynamic Routing | trocar o modelo conforme uma decisão em runtime | `@ChatModelSupplier` |
| Human-in-the-Loop | bloquear o fluxo até uma decisão humana | `@HumanInTheLoop` ou serviço Java com `CompletableFuture` |
| Voting | N agentes decidem a mesma questão e o consenso resolve | `@PlannerAgent` + `VotingPlanner` |
| Blackboard | a ordem dos agentes emerge dos dados no quadro | `@PlannerAgent` + `BlackboardPlanner` |

## A regra do desafio

Uma só regra, com três condições:

1. **Combine no mínimo dois padrões** numa solução que funcione como um todo. Pode usar mais de dois.
2. **Resolva um problema novo**, fora dos casos que já implementamos (currículos, tradução, triagem médica, tickets de TI, desconto B2B, comitê de crédito e war-room de incidentes).
3. **Escolha um dos dez casos** da lista abaixo ou traga o seu, desde que cumpra as duas condições anteriores.

O que vale é a combinação ter motivo: você junta dois padrões quando o problema pede os dois. Combinar só para a solução parecer sofisticada não conta.

## Requisitos

- **Stack do módulo**: Quarkus 3.35.2, Java 25, LangChain4j Agentic e Ollama, como nas aulas 02 a 09.
- **Interface mínima**: exponha o fluxo por WebSocket retornando `Multi<...>`, no formato que você já usou. A interface pode ser simples; o que importa é acompanhar os padrões agindo.
- **Dados de exemplo via LLM**: inclua um `ExampleGenerator` que gere as entradas sintéticas do seu domínio, como nas outras aulas.
- **Declarativo primeiro**: prefira as annotations (`@Agent`, as composições, os planners). Recorra a Java imperativo só onde o padrão pede, como no Human-in-the-Loop com decisão ternária ou estado persistente.

## O que entregar

1. **O projeto rodando** em `quarkus:dev`, com o fluxo funcionando de ponta a ponta.
2. **Um README seu** explicando quais padrões você combinou e por que o problema pede cada um. Essa justificativa é o coração da entrega.
3. **Uma demonstração** do fluxo: suba a aplicação, dispare um caso pela interface e mostre nos logs os padrões em ação (quantas chamadas ao LLM, o que rodou em paralelo, onde o fluxo bloqueou para o humano).

## Como o desafio é avaliado

- **Adequação**: cada padrão escolhido resolve uma característica concreta do problema. Se você tira um padrão e a solução continua a mesma, ele estava só decorando.
- **Composição correta**: as annotations certas, o `AgenticScope` lido no `@Output`, os planners plugados via `@PlannerSupplier` quando o caso pede Voting ou Blackboard.
- **Funciona de verdade**: o fluxo roda com a aplicação no ar, não só compila. Os logs mostram os padrões operando.
- **Clareza**: o seu README faz outra pessoa entender a decisão de design em poucos minutos.

## Armadilhas que já visitamos

Cada aula deixou um aprendizado que vale relembrar antes de combinar:

- **Supervisor com modelo pequeno é frágil**: o planner LLM precisa de um modelo robusto (`deepseek-v4-pro:cloud`) e de `format=json`. Modelo pequeno erra o roteamento.
- **Blackboard não aceita resposta vazia**: um agente que escreve `""` no quadro nunca arma os que dependem dele, e a investigação morre por quiescência. Proíba resposta vazia nos prompts.
- **Human-in-the-Loop ternário pede Java**: `@HumanInTheLoop` cobre o caso binário (aprovar ou rejeitar). Decisão com três saídas ou estado persistente pede `CompletableFuture` com persistência, como na aula 06.
- **Voting e Blackboard moram no artefato `langchain4j-agentic-patterns`**: você os pluga com `@PlannerAgent` mais `@PlannerSupplier`, não com uma annotation própria de workflow.
- **Mix de modelos vai no método**: o `@ModelName` no método `@Agent` é o que a composição lê. Mantenha-o alinhado ao `modelName` do `@RegisterAiService`.
- **Confira o modelo menor nos logs**: prefira um modelo que devolva conteúdo de forma consistente nas tarefas baratas e verifique que cada agente de fato respondeu, em vez de assumir.

## Dez casos para inspirar

Cada caso traz o problema, os padrões que o resolvem, por que esses padrões encaixam e como seria a implementação em linhas gerais. Use como ponto de partida ou como modelo para desenhar o seu.

### 1. Moderação de conteúdo em comunidade online

**O problema.** Uma plataforma com muitas publicações por dia precisa decidir, quase em tempo real, se cada conteúdo viola as políticas (discurso de ódio, golpe, desinformação, conteúdo adulto). Errar custa dos dois lados: barrar quem não devia ou deixar passar o que faz dano. A zona cinzenta é frequente.

**Padrões: Voting + Human-in-the-Loop** (e, se quiser, Dynamic Routing).

**Por que esses padrões.** A decisão é subjetiva, e um único agente decidindo carrega o viés do próprio prompt. Um colegiado de agentes com lentes diferentes votando de forma independente reduz esse viés, que é o argumento do padrão Voting aplicado à moderação. Para os casos de fronteira (empate ou baixa confiança), a palavra final é de um moderador humano, e é aí que entra o Human-in-the-Loop. Dynamic Routing controla o custo no volume alto: modelo barato no conteúdo óbvio, modelo robusto no ambíguo.

**Como implementar.** Cada lente vira um `@Agent` que devolve um voto tipado (viola, categoria, confiança). Um `VotingPlanner` agrega com uma `VotingStrategy` própria, por exemplo um veto qualificado em vez de maioria simples. Quando o resultado fica incerto, o fluxo não publica direto: abre uma pendência e bloqueia numa fila de revisão, do mesmo jeito que o `ApprovalService` do padrão Human-in-the-Loop, liberada quando o moderador decide pela interface.

### 2. Due diligence de contratos

**O problema.** O jurídico recebe contratos (fornecedor, NDA, fusão e aquisição) e precisa revisar riscos por aspecto (cláusulas financeiras, indenização, LGPD, rescisão, trabalhista) e emitir um parecer com recomendação de assinar, ajustar ou recusar. A assinatura é juridicamente vinculante, então não pode ser totalmente automática.

**Padrões: Orchestrator-Workers + Human-in-the-Loop** (e, se quiser, Supervisor).

**Por que esses padrões.** Revisar um contrato decompõe em aspectos independentes, cada um com expertise própria. Rodar esses revisores em paralelo e juntar tudo num parecer é o Orchestrator-Workers, com a vantagem da auditabilidade: o parecer cita cada sub-análise. A decisão de assinar tem responsabilidade legal e fica com o advogado sênior, que aprova, ajusta ou veta antes de o documento seguir (Human-in-the-Loop). Se há tipos muito distintos de contrato, um Supervisor na frente roteia para o pacote de revisores adequado e evita rodar os irrelevantes.

**Como implementar.** Quatro ou cinco `@Agent` revisores num `@ParallelAgent`, mais um sintetizador (modelo robusto) num `@SequenceAgent` com `@Output` lendo o `AgenticScope`. Antes de concluir, o fluxo passa por um gate humano. Como opção, um `@SupervisorAgent` na entrada escolhe o conjunto de revisores conforme o tipo do contrato.

### 3. Correção automatizada de redações

**O problema.** Uma plataforma de ensino corrige redações dissertativas por competências (norma culta, compreensão do tema, argumentação, coesão, proposta de intervenção). A correção humana é cara, lenta e varia de corretor para corretor, e a nota tem peso alto (aprovação, bolsa).

**Padrões: Voting (com média) + Human-in-the-Loop** (e, se quiser, Orchestrator-Workers).

**Por que esses padrões.** A nota de uma redação é subjetiva. Assim como bancas reais usam vários corretores e tiram a média, um colegiado de agentes avaliando de forma independente e agregando por média reduz a variância de um corretor único. Aqui a estratégia de agregação é a média (`VotingStrategy.average`), um contraste útil com a estratégia de maioria do padrão Voting. Quando os votos divergem além de um limite, aciona-se a revisão humana, espelhando o critério das bancas: o Human-in-the-Loop entra só na exceção, em vez de em todo o fluxo. Avaliar cada competência por um agente dedicado (Orchestrator-Workers) deixa cada critério mais sólido.

**Como implementar.** O mesmo avaliador é instanciado como N personas e o `VotingPlanner` agrega as notas por média. Se o desvio entre os votos passa do limite, o fluxo abre uma pendência de revisão humana antes de fechar a nota. A interface mostra a nota por competência, a média e o status de revisão.

### 4. Avaliação de propostas de licitação

**O problema.** Um órgão recebe N propostas para um edital e precisa avaliar cada uma por critérios técnicos e de preço, pontuar, ranquear e homologar o vencedor, com trilha de auditoria, porque a decisão é pública e contestável.

**Padrões: Parallel + Voting** (e, se quiser, Human-in-the-Loop).

**Por que esses padrões.** Há N propostas avaliadas pela mesma rubrica. Aplicar o mesmo avaliador a cada proposta ao mesmo tempo é o `@ParallelMapperAgent`, a mesma mecânica do padrão Parallel, agora como um rubricador para N propostas. Cada proposta isolada é pontuada por uma banca (técnico, jurídico, financeiro) para reduzir viés e dar legitimidade, que é o Voting aplicado por proposta. Os dois eixos de paralelismo (N propostas vezes M jurados) tornam o caso rico de implementar. A homologação final é ato administrativo, então a comissão humana ratifica o ranking (Human-in-the-Loop).

**Como implementar.** Um `@ParallelMapperAgent` roda o avaliador sobre a lista de propostas. Dentro de cada avaliação, um `VotingPlanner` pontua a proposta sob várias lentes. O resultado é um ranking que um gate humano homologa antes da publicação.

### 5. Planejamento e produção de campanha multicanal

**O problema.** O time de marketing precisa lançar uma campanha e produzir peças adaptadas a vários canais (e-mail, Instagram, LinkedIn, blog, push), cada um com formato e tom próprios, mantendo consistência de marca e conformidade (jurídico, claims), com aprovação no final.

**Padrões: Parallel + Orchestrator-Workers** (e, se quiser, Human-in-the-Loop).

**Por que esses padrões.** Gerar a mesma mensagem adaptada a N canais é um copywriter para N canais em paralelo, a mecânica do padrão Parallel levada ao domínio criativo: cada canal recebe uma peça adaptada ao seu formato, em vez de uma cópia do mesmo texto. Cada peça gerada passa por revisores distintos (voz de marca, SEO, jurídico) cujos pareceres se juntam num apto ou ajustar, que é o Orchestrator-Workers na revisão. O responsável de marca aprova antes de publicar (Human-in-the-Loop). O caso é didático porque mostra três etapas combináveis: gerar com Parallel, avaliar com Orchestrator-Workers, aprovar com Human-in-the-Loop.

**Como implementar.** Um `@ParallelMapperAgent` gera as peças por canal. Cada peça entra num `@ParallelAgent` de revisores com um sintetizador. O gate humano aprova o lote antes da publicação.

### 6. Antifraude em pagamentos com contestação do titular

**O problema.** A cada transação é preciso decidir em milissegundos se ela é legítima, suspeita ou fraude, equilibrando bloquear a fraude real e não atritar o cliente bom. Os sinais são heterogêneos (geolocalização, device, padrão de gasto, listas restritivas).

**Padrões: Dynamic Routing + Human-in-the-Loop** (e, se quiser, Voting).

**Por que esses padrões.** A maioria das transações é claramente boa e deve passar por um modelo barato e rápido; só as ambíguas merecem um modelo robusto. Trocar o modelo conforme o risco é o `@ChatModelSupplier` do padrão Dynamic Routing, agora guiado por um score de risco, o que aqui é decisão de custo e de latência ao mesmo tempo. O detalhe do caso está em quem ocupa o papel de humano: aqui é o próprio titular da conta. Ele recebe um aviso "foi você?" e a resposta dele libera ou bloqueia a transação. É o mesmo bloqueio até a decisão humana do padrão Human-in-the-Loop, agora com o cliente no papel de humano. Os sinais de fraude ainda podem formar um colegiado (Voting) antes do roteamento.

**Como implementar.** Um classificador de risco define o tier e o `@ChatModelSupplier` escolhe o `ChatModel`. Transações de alto risco abrem um desafio ao titular (Human-in-the-Loop por push ou WebSocket, com timeout: sem resposta, vale a política conservadora). A interface mostra o fluxo de transações com score, modelo usado e status do desafio.

### 7. Sala de pesquisa científica

**O problema.** Um pesquisador faz uma pergunta ampla ("o composto X tem efeito sobre Y?"). É preciso coletar evidências de fontes heterogêneas (artigos, patentes, bases de dados, resultados experimentais), correlacionar, levantar hipóteses e produzir uma síntese com nível de confiança e contradições explícitas.

**Padrões: Blackboard + Voting** (e, se quiser, Orchestrator-Workers).

**Por que esses padrões.** A investigação não tem ordem fixa: cada fonte contribui quando os dados de que ela precisa aparecem no quadro, e as hipóteses se refinam conforme novas evidências chegam. Essa ativação por dados é o padrão Blackboard, aqui aplicado a um problema diferente: descoberta com hipóteses concorrentes, em vez de uma causa raiz única, o que ajuda você a ver o mesmo padrão servindo a outro domínio. Uma conclusão científica não deve sair de um único agente, então um júri verifica se ela se sustenta nas evidências (o Voting como júri), marcando confiança e dissenso.

**Como implementar.** Um `@PlannerAgent` com `BlackboardPlanner` e fontes como agentes, cada uma escrevendo a própria evidência no quadro. Um correlacionador levanta hipóteses e, antes de concluir, um `VotingPlanner` julga cada hipótese como sustentada, refutada ou incerta. O objetivo do quadro é a síntese verificada.

### 8. Centro de operações logísticas

**O problema.** Uma transportadora tem centenas de entregas em curso ao mesmo tempo. O dia inteiro chegam exceções (atraso no trânsito, veículo com defeito, cliente ausente, endereço errado), e cada uma precisa ser interpretada e resolvida (reagendar, trocar a rota, remanejar o veículo, avisar o cliente). A prioridade muda conforme a gravidade e o efeito sobre as outras entregas.

**Padrões: Supervisor + Blackboard** (e, se quiser, Human-in-the-Loop).

**Por que esses padrões.** Quando uma exceção chega, alguém precisa ler o que aconteceu e encaminhar para o tratamento certo, e esse é o papel do Supervisor: um planner que classifica a exceção e a roteia. A resolução em si não segue um roteiro fixo: o estado das entregas é um quadro vivo, e cada ação (um novo prazo, uma nova rota) habilita a próxima (recalcular a carga, notificar o cliente) conforme aparece no quadro. Essa ordem que emerge dos dados é o Blackboard. Os dois se encadeiam bem: o Supervisor decide por onde começar e o Blackboard deixa o resto se organizar sozinho. Reroteamentos caros, que estouram custo ou quebram SLA, podem pedir o aval do despachante (Human-in-the-Loop).

**Como implementar.** Um `@SupervisorAgent` recebe a exceção e a roteia para a família de tratamento adequada. O tratamento roda sobre um `@PlannerAgent` com `BlackboardPlanner`, onde cada ação é um agente com os seus `@V`: o de notificação só dispara quando há um novo prazo no quadro, o de rebalanceamento só quando a rota muda. O quadro converge para um plano de entregas atualizado, e as ações de alto custo passam por um gate humano.

### 9. Comitê de investimento em startups

**O problema.** Um fundo recebe muitos pitches de startups e precisa avaliar cada um por várias dimensões (tamanho de mercado, time, produto, tração, riscos) e decidir investir, passar ou pedir mais informações. A decisão move muito dinheiro e tem bastante subjetividade.

**Padrões: Orchestrator-Workers + Voting.**

**Por que esses padrões.** Avaliar uma startup decompõe em dimensões independentes, cada uma com expertise própria. Rodar um analista por dimensão em paralelo e juntar tudo num dossiê é o Orchestrator-Workers, com a auditabilidade de o dossiê mostrar a leitura de cada dimensão. A decisão de investir não sai de uma cabeça só: um comitê de sócios com perfis diferentes (mercado, técnico, financeiro) lê o dossiê e vota, e o consenso resolve, que é o Voting. O caso é didático porque encadeia dois usos distintos de vários agentes: primeiro decompor o problema, depois decidir por colegiado.

**Como implementar.** Um `@ParallelAgent` com os analistas por dimensão e um sintetizador montam o dossiê (`@SequenceAgent` com `@Output` lendo o `AgenticScope`). Esse dossiê alimenta um `VotingPlanner` onde os sócios votam investir, passar ou aprofundar, com uma `VotingStrategy` que pode exigir quórum. A interface mostra o dossiê por dimensão e o placar do comitê.

### 10. Análise e liquidação de sinistros de seguro

**O problema.** O segurado abre um sinistro (auto, residencial, vida) e é preciso identificar a linha, avaliar a cobertura e indícios de fraude, estimar o valor e decidir pagar, negar ou investigar, com pagamentos altos exigindo alçada.

**Padrões: Supervisor + Dynamic Routing** (e, se quiser, Human-in-the-Loop).

**Por que esses padrões.** O sinistro precisa ir ao perito da linha certa (auto, vida e residencial são especialidades distintas), e um planner LLM que lê a descrição e escolhe o especialista é o `@SupervisorAgent` do padrão Supervisor. Os sinistros simples e de baixo valor podem ser resolvidos por um modelo barato; os complexos ou de alto valor escalam para o robusto, e trocar o modelo conforme valor e suspeita é o `@ChatModelSupplier`. O caso ensina dois roteamentos combinados que roteiam coisas diferentes: o Supervisor escolhe o agente (qual perito), o Dynamic Routing escolhe o modelo (quão robusto). Acima da alçada, a liberação do pagamento pede aprovação humana (Human-in-the-Loop).

**Como implementar.** Um `@SupervisorAgent` com `@SupervisorRequest` roteia para o perito da linha. O handler usa o `ChatModel` conforme valor e risco via `@ChatModelSupplier`. Acima da alçada, um gate humano libera o pagamento. Um `@SequenceAgent` encadeia roteamento, avaliação e aprovação.

## Por onde começar

1. Escolha o caso (um dos dez ou o seu) e escreva em uma frase qual problema ele resolve.
2. Liste os padrões candidatos e teste cada um com a pergunta: se eu tirar este padrão, a solução ainda resolve o problema? O que sobrevive ao corte é o que você precisa.
3. Comece pelo caminho feliz com dados do `ExampleGenerator`, um padrão de cada vez, e só então junte tudo.
4. Suba a aplicação e confirme nos logs que cada padrão está agindo.

Quando o fluxo rodar de ponta a ponta e você reconhecer cada padrão no log, o desafio está cumprido.
