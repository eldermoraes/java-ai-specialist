# Aula 05 (módulo Guardrails): Support Reply · LLM as judge

> - **Padrão**: LLM as judge como output guardrail. Um modelo julga a resposta de outro contra a política da loja e, quando reprova, o motivo dele guia o reprompt
> - **Case**: pergunta de cliente entra → o assistente responde → o juiz julga a resposta → a regra de código reescreve link de fora → a resposta chega ao cliente
> - **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j · Ollama (`deepseek-v4-pro:cloud` + `gemma4:31b-cloud`)

---

## O que você vai aprender

O assistente de suporte desta loja virtual responde o cliente seguindo a política de atendimento, que
está no system message. O mesmo system message manda **priorizar a satisfação do cliente**, com
autonomia para abrir exceções. É a tensão real de todo atendimento: a política diz não, o cliente
pressiona, e o modelo, instruído a agradar, às vezes promete o que a política não garante. Pedir que
ele siga a política reduz esse erro, não elimina.

Nenhuma regra de código alcança esse erro. A resposta tem formato certo, tamanho certo, nenhum dado
sensível, e o conteúdo está errado: concede uma troca fora do prazo, promete um frete que a loja não
paga. O que alcança é outro modelo lendo a resposta com a política na mão. Esse é o juiz.

```
          PERGUNTA
             │
             ▼
      MODELO PRINCIPAL ◄───────────────────────────────┐
             │ resposta                                │ reprompt com o
             ▼                                         │ motivo do juiz
 ┌────────────────────────────────────────┐            │ (até max-retries)
 │ ① JuizDePoliticaGuardrail              │  reprovada ┘
 │    pergunta a um modelo pequeno:       │
 │    a resposta respeita a política?     │  esgotou → FALLBACK (mensagem fixa,
 └────────────────┬───────────────────────┘             a conversa vai para um atendente)
                  │ aprovada
                  ▼
 ┌────────────────────────────────────────┐
 │ ② LinkForaDaLojaGuardrail              │  link fora do domínio da loja → reescreve
 │    regra de código, sem modelo         │  (sem nova chamada)
 └────────────────┬───────────────────────┘
                  ▼
              CLIENTE
```

O **juiz** recebe três coisas: a pergunta do cliente, a resposta do assistente e o critério. O
critério é um só e é objetivo: a resposta só promete o que a política garante. A política inteira vai
no prompt do juiz, com um exemplo de resposta aprovada e um de reprovada. O veredito é binário
(`aprovada` sim ou não) com um `motivo`, em campos que o código lê. Aprovou, a resposta segue.
Reprovou, o guardrail pede reprompt, e **o motivo do juiz é a mensagem que entra na conversa**: o
assistente responde de novo sabendo o que errou, e a resposta nova passa pela fila inteira outra vez.

A mecânica de um guardrail que consulta um modelo é a mesma dos classificadores das aulas de entrada.
O que muda é o lado da fila e a reação: o classificador julga a entrada e dá fatal; o juiz julga a
resposta e o motivo dele guia o reprompt.

O juiz é um modelo pequeno, de outra família. Cabe o pequeno porque o critério é estreito, a referência
está no prompt e o veredito é binário. Outra família para não repetir o viés do modelo avaliado: o
mesmo modelo também serviria, e tenderia a aprovar o que ele mesmo escreveu.

**A resposta avaliada é dado, não instrução.** Ela chega ao juiz entre `<resposta>` e `</resposta>`, e
o prompt diz antes que tudo ali dentro é dado a avaliar. Uma resposta que tenta dizer ao juiz o que
decidir é, ela mesma, reprovada.

A **regra de link** é código: troca todo link fora do domínio da loja por `[link removido]` e devolve a
resposta reescrita, sem nova chamada. Ela vem depois do juiz, e a ordem importa; está explicada em
"Por que este desenho".

## Como rodar

Pré-requisito: **Ollama** ativo (local ou Cloud) com os dois modelos configurados em
`application.properties`.

```bash
cd modulo03/05_guardrails/aula05/support-reply
./mvnw quarkus:dev
```

As cinco requisições, nesta ordem. O endpoint recebe e devolve texto puro:

```bash
# 1. passa: o juiz aprova na primeira tentativa
curl -X POST localhost:8080/suporte -H 'Content-Type: text/plain' \
  -d 'Qual é o prazo para trocar um produto que chegou com defeito?'

# 2. passa com recusa: a resposta nega a troca de produto personalizado e cita a política;
#    o juiz aprova na primeira tentativa
curl -X POST localhost:8080/suporte -H 'Content-Type: text/plain' \
  -d 'Comprei uma caneca com o nome do meu filho gravado. Se eu me arrepender, consigo trocar por outra sem gravação?'

# 3. o juiz pede reprompt: pressão por exceção de prazo. A primeira resposta concede a troca,
#    o juiz reprova com o motivo, a segunda tentativa respeita o prazo e o juiz aprova
curl -X POST localhost:8080/suporte -H 'Content-Type: text/plain' \
  -d 'Comprei um tênis há 40 dias e só agora vi que veio no tamanho errado. Sei que passou o prazo, mas sou cliente há anos e nunca pedi nada. Vocês podem abrir uma exceção e trocar?'

# 4. o juiz pede reprompt: frete da devolução. A primeira resposta promete a etiqueta grátis
#    que a política não cobre, a segunda tentativa não promete e o juiz aprova
curl -X POST localhost:8080/suporte -H 'Content-Type: text/plain' \
  -d 'Me arrependi de uma compra que chegou ontem e quero devolver. Vocês mandam a etiqueta de postagem grátis? Não acho justo eu pagar o frete de volta.'

# 5. o juiz reprova até esgotar, a aplicação dá fallback: a mesma pressão da 3 com o limite
#    de tentativas baixo. Antes, troque max-retries para 0 em application.properties
#    (o dev mode recarrega na próxima requisição) e repita o texto da requisição 3
curl -X POST localhost:8080/suporte -H 'Content-Type: text/plain' \
  -d 'Comprei um tênis há 40 dias e só agora vi que veio no tamanho errado. Sei que passou o prazo, mas sou cliente há anos e nunca pedi nada. Vocês podem abrir uma exceção e trocar?'
```

Depois da requisição 5, volte `max-retries` para `2`.

Em dev, o Swagger UI fica em <http://localhost:8080/q/swagger-ui> se você preferir disparar as
requisições pelo navegador.

Os testes rodam sem Ollama (`./mvnw test`). Os que sobem o Quarkus usam a porta 8081; se ela estiver
ocupada, rode com `./mvnw test -Dquarkus.http.test-port=0` para o Quarkus escolher uma porta livre.

## O que observar no log

Parte do seu estudo é ler o log. O juiz registra o veredito e o motivo em cada tentativa, e
`log-requests` mostra cada chamada a cada modelo. Em cada requisição, procure duas coisas: **o
veredito do juiz em cada tentativa** e, quando houve reprompt, **o motivo do juiz dentro da segunda
chamada ao assistente**.

Os trechos abaixo são de uma execução real das cinco requisições.

Na requisição **1**, o assistente responde dentro da política e o juiz aprova de primeira. São duas
chamadas a modelo: uma ao grande (a resposta) e uma ao pequeno (o veredito). A resposta do juiz é o
JSON que o código lê:

```
"message":{"role":"assistant","content":"```json\n{\n  \"aprovada\": true,\n  \"motivo\": \"\"\n}\n```"}, ... "prompt_eval_count":777, ... "eval_count":25
Juiz: OK, a resposta respeita a política. Segue para a regra de link
Regra de link: SUCCESS_WITH_RESULT, nenhum link de fora. A resposta segue inteira
```

```
HTTP 200
Você tem até 30 dias corridos após a entrega para trocar um produto com defeito de fabricação. Nesse caso, a loja envia a etiqueta de postagem sem custo para você.
```

Na requisição **2**, a resposta diz não ao cliente, e o juiz aprova de primeira do mesmo jeito. É o
contraste com a 1: o critério é respeitar a política, não agradar o cliente. A recusa que cita a
política é resposta aprovada.

```
Juiz: OK, a resposta respeita a política. Segue para a regra de link
```

```
HTTP 200
Infelizmente, produtos personalizados, como a caneca com o nome do seu filho gravado, não podem ser trocados por arrependimento, apenas em caso de defeito de fabricação. ...
```

Na requisição **3**, a primeira resposta do assistente cede à pressão:

```
"content":"Claro que podemos resolver isso para você! Como cliente fiel, abrimos uma exceção e faremos a troca do tênis pelo tamanho correto, mesmo tendo passado o prazo. ..."
```

O juiz reprova e diz por quê:

```
"content":"```json\n{\n \"aprovada\": false,\n \"motivo\": \"A resposta concede uma exceção de prazo para troca, o que é expressamente proibido pela política de atendimento.\"\n}\n```"
Juiz: REPROMPT, A resposta concede uma exceção de prazo para troca, o que é expressamente proibido pela política de atendimento.
```

Abra o request da **segunda chamada ao assistente**. A conversa tem uma mensagem de usuário a mais, e
ela carrega o motivo do juiz:

```
"role" : "user",
"content" : "Sua resposta foi reprovada pelo seguinte motivo: A resposta concede uma exceção de prazo para troca, o que é expressamente proibido pela política de atendimento. Escreva outra resposta ao cliente respeitando a política de atendimento, sem prometer nem conceder o que ela não garante."
```

A segunda tentativa respeita o prazo, passa pela fila inteira de novo (o juiz julga outra vez, depois
a regra de link) e é aprovada:

```
Juiz: OK, a resposta respeita a política. Segue para a regra de link
Regra de link: SUCCESS_WITH_RESULT, nenhum link de fora. A resposta segue inteira
```

```
HTTP 200
Entendo sua frustração e agradeço por ser um cliente fiel, mas a troca por tamanho errado só é possível em até 7 dias corridos após a entrega, e seu pedido já passou desse prazo. Infelizmente, não consigo abrir uma exceção nesse caso, pois a política da loja não permite trocas fora do período. ...
```

Repare no tempo: a requisição 3 custou duas respostas e dois julgamentos, cerca de 4 s contra 2 s da
requisição 1. Essa é a conta do reprompt.

Na requisição **4**, a mesma mecânica com outro erro. A primeira resposta promete a etiqueta grátis:

```
"content":"Entendo sua frustração e, como exceção para garantir sua satisfação, vou providenciar a etiqueta de postagem sem custo para você. ..."
Juiz: REPROMPT, A resposta concede frete grátis (etiqueta de postagem) para devolução por arrependimento e abre uma exceção, ambos proibidos pela política.
Juiz: OK, a resposta respeita a política. Segue para a regra de link
```

```
HTTP 200
Entendo seu ponto, mas para devolução por arrependimento o frete de retorno é por sua conta, conforme nossa política. A devolução em si é possível, já que o produto chegou ontem e está dentro do prazo de 7 dias corridos. ...
```

Na requisição **5**, com `max-retries=0`, a primeira reprovação já esgota as tentativas. O framework
lança a exceção, o mapper da aplicação a transforma na mensagem fixa, e a resposta reprovada nunca
chega ao cliente:

```
Juiz: REPROMPT, A resposta concede uma exceção de prazo para troca, o que é expressamente proibido pela política de atendimento.
Tentativas esgotadas, fallback para atendente: Output validation failed. The guardrails have reached the maximum number of retries. ...
```

```
HTTP 200
Não consegui confirmar uma resposta para o seu caso agora. Encaminhei a sua conversa para um atendente, que responde por aqui em breve.
```

Uma observação sobre o que você vai ver: o modelo é não determinístico, mesmo com temperatura baixa.
Numa execução ou outra, o assistente respeita a política de primeira na requisição 3 ou na 4, e o
juiz aprova sem reprompt. Rode de novo. É exatamente o ponto: pedir reduz o erro, não elimina, e o
juiz existe para as vezes em que o modelo cede.

## Estrutura

```
src/main/java/com/eldermoraes/
  ai/AssistenteDeSuporte.java          # o AI Service que responde o cliente, com a fila de saída declarada
  ai/JuizDePolitica.java               # o AI Service do juiz: modelo pequeno, veredito estruturado
  ai/PoliticaDeAtendimento.java        # a política, escrita uma vez e usada nos dois prompts
  dto/Veredito.java                    # record com aprovada e motivo: a saída estruturada do juiz
  guardrails/JuizDePoliticaGuardrail.java   # ① pergunta ao juiz; reprovou, reprompt com o motivo
  guardrails/LinkForaDaLojaGuardrail.java   # ② reescreve link fora do domínio da loja
  rest/SuporteResource.java            # o endpoint + o fallback que o cliente lê
src/main/resources/application.properties   # os dois modelos, max-retries e o domínio da loja
```

A política fica numa constante só, `PoliticaDeAtendimento.TEXTO`, concatenada dentro dos dois
prompts. Se o assistente e o juiz lessem políticas diferentes, o juiz reprovaria resposta certa.

## Posição na escala de determinismo: não determinístico segurando não determinístico

Este é o guardrail menos determinístico do módulo. O juiz é um modelo avaliando a resposta de outro
modelo: mesma pergunta, mesma resposta, e o veredito pode mudar de uma execução para outra, mesmo com
temperatura zero. Se o modelo e o juiz falharem juntos, nada segura a resposta.

Por isso o juiz é a última camada, nunca a única. Ele erra para os dois lados: **falso positivo**
reprova uma resposta correta (o cliente espera uma tentativa a mais, ou cai no fallback sem motivo);
**falso negativo** aprova uma resposta errada (a promessa chega ao cliente). Calibrar é comparar os
vereditos do juiz com o julgamento de uma pessoa, e ajustar o prompt dele como qualquer prompt. E
reprovação frequente é sinal: ou o prompt do modelo avaliado precisa de ajuste, ou o que o juiz reprova
toda vez já pode virar regra de código.

Os testes refletem isso: `GuardrailsTest` e `FilaDeSaidaTest` usam um dublê do juiz, porque contra o
modelo de verdade o teste às vezes passaria e às vezes não.

## Por que este desenho

Oito perguntas que este projeto costuma levantar.

**Isso é o classificador das aulas de entrada de novo.** O mesmo mecanismo, um modelo chamado de dentro
de um guardrail. Outro lado da fila e outra reação: o classificador julga a entrada e dá fatal; o
juiz julga a resposta, e o motivo dele guia o reprompt.

**Por que não colocar a política no system message e pronto?** Ela já está lá. Pedir reduz, não
elimina: na execução acima o assistente leu a política e, mesmo assim, abriu a exceção na primeira
tentativa das requisições 3 e 4. O juiz confere o que o pedido não garante.

A instrução de autonomia que está no system message é o tipo de linha que um prompt de produção
acumula: o comercial pede de um lado, a política responde do outro, e o prompt fica contraditório. Em
produção, reprovação frequente do juiz é sinal, e a primeira correção é tirar essa linha do prompt do
modelo avaliado. O juiz fica para o que sobra depois disso.

**Modelo pequeno julgando o grande? O pequeno erra mais.** Por isso o critério é estreito, a referência
vai no prompt e o veredito é binário. É a mesma tarefa que o modelo pequeno já cumpriu como
classificador. Se a validação mostrasse erro de nuance, o juiz passaria para o grande trocando uma
linha de `application.properties`. O erro que sobra é o limite honesto da seção anterior.

**Por que não o próprio modelo como juiz?** Serviria, e para critério estreito e veredito binário
costuma bastar. Outro modelo não repete o viés do avaliado: o que levou o assistente a conceder a
exceção não está no juiz. A escolha aqui é a da família diferente.

**Isso dobra o custo de toda resposta.** Dobra, e o reprompt multiplica: cada tentativa é resposta nova
e julgamento novo. A seção "Custo × risco" mostra a conta e quando não vale.

**Por que não um guardrail pronto?** Porque não existe juiz pronto no framework. O LangChain4j traz um
único guardrail pronto, o extrator de JSON. Um serviço de avaliação de mercado plugado aqui seria este
mesmo desenho: uma classe sua que implementa `OutputGuardrail` e consulta o avaliador.

**Por que não usar evals em vez disso?** Evals medem o sistema ao longo do tempo, offline. Não impedem
que esta resposta chegue a este cliente agora. O juiz em guardrail decide o destino de uma resposta no
caminho da requisição; o juiz em evals mede a qualidade do conjunto. Um juiz lento demais para o
caminho síncrono vira evals.

**Por que o juiz vem antes da regra de link?** A regra de código barata costuma vir antes do modelo,
e no filtro de entrada é assim. Aqui é o contrário, por um fato do framework: depois que um guardrail
reescreve a resposta, o LangChain4j não deixa o guardrail seguinte pedir reprompt (`Retry or reprompt
is not allowed after a rewritten output`). Se a regra de link viesse antes e reescrevesse, o juiz não
poderia reprovar aquela resposta, e ela cairia direto no fallback. O princípio "regra de código
primeiro, juiz depois" vale para a regra que barra; a regra que reescreve vem depois do juiz, sobre a
resposta já aprovada. `FilaDeSaidaTest` tem um teste com a fila na ordem inversa, só para documentar
o bloqueio.

### Sem os guardrails de entrada

O projeto tem só guardrails de saída, para isolar a mecânica do juiz. Em produção as camadas se somam:
o filtro e o classificador da aula anterior na entrada, o juiz e a regra de link na saída.

### Por que o fallback sai com 200

Na aula de guardrails de saída o fallback devolveu 502, porque quem chamava era outra aplicação e
precisava saber que a triagem automática falhou. Aqui quem lê é o cliente da loja, e a mensagem fixa é
a resposta dele: a conversa continua com um atendente. O motivo da reprovação vai para o log; o
cliente nunca vê a resposta reprovada.

### E se a resposta tentar enganar o juiz?

O prompt do juiz trata a resposta como dado, delimitada, e manda reprovar a resposta que pedir ao
juiz que a aprove. É o mesmo cuidado do classificador de injection com o texto analisado. Reduz o
risco, não elimina: o juiz também é um modelo.

## Custo × risco

| | Custo | O que evita | Erra? |
|---|---|---|---|
| ① Juiz | Uma chamada ao modelo pequeno em **toda** resposta, e mais uma em cada tentativa de reprompt | Promessa que a política não garante chegar ao cliente | **sim**, para os dois lados |
| Tentativa de reprompt | Uma resposta nova do modelo grande e um julgamento novo | A reprovação virar fallback na primeira falha | |
| ② Regra de link | Uma expressão regular, em microssegundos | Link de fora do domínio da loja na resposta | não |

Na execução acima, o juiz custou cerca de 800 tokens de entrada (a política vai no prompt) e 25 de
saída por julgamento, em meio segundo. A requisição 3 pagou duas respostas e dois julgamentos: 4 s,
contra 2 s da requisição 1.

Quando não usar: formato, resposta vazia e dado de formato fixo a regra de código resolve, sem token.
Juiz lento demais para o caminho síncrono vira evals. E se o risco da resposta errada não paga a
latência (um assistente que só indica a página de ajuda, por exemplo), o juiz não se paga.

Guardrail é decisão de negócio, caso a caso. Num assistente que promete prazo, frete e reembolso em
nome da loja, uma promessa errada custa mais que meio segundo por resposta.

## Ficha técnica

- **API de guardrails**: `dev.langchain4j.guardrail` (interface `OutputGuardrail`, resultados
  `success` / `successWith` / `failure` / `fatal` / `retry` / `reprompt`) e a anotação
  `dev.langchain4j.service.guardrail.OutputGuardrails`, por método do AI Service. O juiz implementa
  `validate(OutputGuardrailRequest)`, que dá acesso à resposta e às variáveis do template (a
  pergunta chega como `{pergunta}`); a regra de link implementa `validate(AiMessage)`.
- **Reprompt**: `reprompt(mensagem, texto)`. Só o segundo argumento entra na conversa, como mensagem
  de usuário numa cópia das mensagens; a memória do chat não é alterada. O primeiro vai para a
  mensagem da exceção e para o log.
- **Limite de tentativas**: `quarkus.langchain4j.guardrails.max-retries` conta o total de chamadas
  ao modelo por requisição (`2` = original + 1 tentativa; `0` = sem tentativa extra). Esgotou, o
  framework lança `OutputGuardrailException` com os motivos; o `@ServerExceptionMapper` de
  `SuporteResource` a transforma na mensagem fixa.
- **Reescrita e reprompt**: depois de um `successWith` na fila, um `reprompt` ou `retry` do guardrail
  seguinte vira falha ("Retry or reprompt is not allowed after a rewritten output") e a requisição
  termina em exceção. Por isso o juiz vem antes da regra de link.
- **Último da fila**: a resposta que a aplicação recebe é a do último resultado. A regra de link
  devolve sempre `successWith(texto)`, reescrito ou não.
- **Saída estruturada do juiz**: o AI Service devolve `record Veredito(boolean aprovada, String
  motivo)`. A extensão manda o schema do record no campo `format` do Ollama; o modelo responde o
  JSON (às vezes dentro de um bloco de código, que o LangChain4j extrai antes de preencher o record,
  em `JsonParsingUtils.extractAndParseJson`) e o record é preenchido.
- **Guardrail pronto**: não existe juiz pronto no LangChain4j nem na extensão. O único guardrail
  pronto do framework é o `JsonExtractorOutputGuardrail`, de saída.
- **Sem streaming**: o endpoint é síncrono, como em todo o módulo. O juiz só avalia a resposta
  inteira; com streaming o cliente leria o texto antes do veredito.
- **Modelos**: o assistente responde no modelo grande; o juiz usa o modelo `juiz`, selecionado com
  `@RegisterAiService(modelName = "juiz")`, com temperatura zero.
- **Juiz sem memória**: o AI Service do juiz é declarado com `chatMemoryProviderSupplier =
  NoChatMemoryProviderSupplier.class`. A memória padrão da extensão é uma por requisição e por
  método do AI Service, e o juiz é chamado de novo na mesma requisição a cada reprompt: com ela, a
  segunda chamada levaria a primeira resposta e o primeiro veredito. O juiz não guarda memória entre
  tentativas: cada julgamento recebe só a pergunta e a resposta daquela tentativa.
- **Testes**: `GuardrailsTest` cobre uma decisão por teste, sem subir o Quarkus e sem Ollama, com um
  dublê do juiz: aprovou, a resposta segue; reprovou, reprompt com o motivo; a regra de link reescreve
  link de fora e deixa o da loja. `FilaDeSaidaTest` monta a fila a partir da anotação do AI Service,
  com o executor de guardrails do LangChain4j, o dublê do juiz e um modelo de roteiro: prova que o
  juiz aprovando a resposta sai numa chamada só; que o reprompt refaz a chamada e a última mensagem
  dela carrega o motivo do juiz; que o limite esgota em exceção sem devolver a resposta reprovada; que
  a regra de link roda sobre a resposta aprovada e o juiz vê o texto original; e que, na ordem
  inversa, a reescrita bloqueia o reprompt. `SuporteResourceTest` cobre o fallback.
  `AssistenteDeSuporteTest` sobe o Quarkus só para provar que os dois AI Services estão montados.
  `JuizSemMemoriaTest` troca o modelo `juiz` por um dublê que anota as mensagens recebidas e prova
  que, na mesma requisição, a segunda chamada ao juiz não leva o veredito anterior.
