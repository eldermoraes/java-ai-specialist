# Aula 03 (módulo Guardrails): Ticket Triage · guardrails de saída

> - **Padrão**: três guardrails de saída encadeados depois de um AI Service, e as reações possíveis a uma reprovação
> - **Case**: chamado de suporte entra em texto livre → o modelo responde → formato → regra de negócio → dado sensível → só então a aplicação lê
> - **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j · Ollama (`deepseek-v4-pro:cloud`)

---

## O que você vai aprender

Um guardrail de saída é uma classe Java que roda **depois** da resposta do modelo e antes de
ela ser devolvida. Ele recebe a resposta inteira, nunca em pedaços, e decide se ela passa.

A validação é regra de código: desserializar um JSON, comparar um valor com uma lista,
procurar um CPF. Não tem modelo nem token envolvido. O que muda em relação à entrada é a
reação: quando a resposta é reprovada, uma das saídas possíveis é refazer a chamada. Aí o
modelo volta para a conta, e com ele o custo e o não determinismo.

Este projeto tem três guardrails, e a fila roda na ordem em que eles aparecem na anotação:

```
             MODELO
                │
                ▼
 ┌──────────────────────────────┐
 │ ① FormatoGuardrail           │  vazia → RETRY (refaz a mesma chamada)
 │    desserializa a resposta   │  não é JSON → REPROMPT (refaz com o motivo)
 │                              │  campo ausente → REPROMPT
 │                              │  dentro de cerca de código → reescreve
 └──────────────┬───────────────┘
                ▼
 ┌──────────────────────────────┐
 │ ② RegraDeNegocioGuardrail    │  prioridade fora do conjunto → REPROMPT
 │    confere o valor do campo  │  "Alta", "média" → reescreve (normaliza)
 └──────────────┬───────────────┘
                ▼
 ┌──────────────────────────────┐
 │ ③ DadoSensivelGuardrail      │  CPF → reescreve (mascara)
 │    expressão regular         │
 └──────────────┬───────────────┘
                ▼
           APLICAÇÃO
```

O formato vem primeiro porque os outros dois precisam da resposta já lida: não dá para
validar o campo `prioridade` antes de saber que existe um campo `prioridade`. E um guardrail
que reescreve muda o que o próximo enxerga: o segundo recebe o JSON já sem a cerca de código,
o terceiro recebe a prioridade já normalizada.

São quatro reações possíveis a uma reprovação, e a diferença entre elas é o que você precisa
levar desta aula.

**Retry** refaz a mesma chamada, sem dizer o que estava errado. Serve para o erro que não tem
correção a pedir: resposta vazia. Não há o que instruir, só tentar de novo.

**Reprompt** refaz a chamada informando o motivo. O motivo entra na conversa como uma
mensagem de usuário nova, e é isso que dá ao modelo a chance de corrigir. Serve para o erro
que o modelo corrige quando avisado: formato errado, campo faltando, valor fora do conjunto.

**Reescrever** é o caminho que não é passar nem reprovar: o código corrige a resposta e ela
segue, sem nova chamada. Normalizar `Alta` para `alta` e mascarar um CPF são assim. Custam uma
expressão regular, e o usuário não espera nada a mais por isso.

**Fatal** reprova e aborta sem nova tentativa. Nenhum guardrail daqui usa essa saída, porque
nos três casos deste projeto chamar o modelo de novo tem utilidade.

Duas coisas valem para qualquer uma dessas reações.

**A resposta reprovada nunca chega ao usuário.** O que sai é uma resposta aprovada ou o
fallback que a aplicação escolheu. Quem define esse fallback é o `TriagemResource`, não o
framework.

**Retry e reprompt refazem a chamada inteira, e a fila inteira roda de novo** sobre a nova
resposta. Cada tentativa é uma pergunta completa ao modelo: token e latência somam, e o
usuário espera pelas duas. É por isso que o limite de tentativas é configuração da aplicação,
e não um detalhe.

## Como rodar

Pré-requisito: **Ollama** ativo (local ou Cloud) com o modelo configurado em
`application.properties`.

```bash
cd modulo03/05_guardrails/aula03/ticket-triage
./mvnw quarkus:dev
```

O endpoint recebe e devolve texto puro:

```bash
# 1. passa: a resposta já vem no formato pedido
curl -X POST localhost:8080/triagem -H 'Content-Type: text/plain' \
  -d 'A VPN cai toda hora desde ontem e não consigo acessar o ERP'

# 2. reprompt corrige: o mesmo chamado, pelo método cuja instrução não pede o formato
curl -X POST localhost:8080/triagem/livre -H 'Content-Type: text/plain' \
  -d 'A VPN cai toda hora desde ontem e não consigo acessar o ERP'

# 3. mascara: o CPF volta na resposta e sai antes da aplicação
curl -X POST localhost:8080/triagem -H 'Content-Type: text/plain' \
  -d 'O colaborador de CPF 123.456.789-00 não consegue emitir o holerite'

# 4. esgota: troque max-retries para 0 em application.properties e repita a requisição 2
```

A requisição 4 não tem comando novo: é a 2 outra vez, depois de trocar
`quarkus.langchain4j.guardrails.max-retries` de `2` para `0` em `application.properties`. O
dev mode recarrega na próxima requisição, sem reiniciar nada. Volte o valor para `2` depois.

Em dev, o Swagger UI fica em <http://localhost:8080/q/swagger-ui> se você preferir disparar as
requisições pelo navegador.

## O que observar no log

Parte do seu estudo é ler o log. Cada guardrail registra a decisão que tomou e o motivo, e
`log-requests` mostra cada chamada ao modelo, então dá para contar quantas aconteceram.

Na requisição **1**, a resposta já vem no formato e os três dizem OK de primeira. Um request
ao modelo, e nada a corrigir:

```
Formato: OK, JSON com os três campos
Regra: OK, prioridade dentro do conjunto
Dado sensível: SUCCESS_WITH_RESULT, nada encontrado. A resposta segue inteira
```

Na requisição **2** está a aula. A primeira resposta vem em prosa, o formato reprova e pede
reprompt:

```
Formato: REPROMPT, resposta fora do formato JSON
```

E aí aparece o segundo request ao modelo, com o motivo dentro da conversa. Repare na última
mensagem: ela não estava no prompt original, foi o guardrail que a colocou ali.

```
HTTP request:
- body: {
  "messages" : [ {
    "role" : "system",
    "content" : "Você faz a triagem dos chamados de suporte interno da empresa.\n\n..."
  }, {
    "role" : "user",
    "content" : "A VPN cai toda hora desde ontem e não consigo acessar o ERP"
  }, {
    "role" : "assistant",
    "content" : "**Categoria:** Conectividade / VPN  \n**Prioridade:** Alta  \n**Resumo:** ..."
  }, {
    "role" : "user",
    "content" : "Responda apenas com um JSON com os campos categoria, prioridade e resumo, sem texto fora do JSON."
  } ],
```

A segunda resposta vem em JSON, dentro de uma cerca de código e com a prioridade em
maiúscula. Nenhuma das duas coisas precisa de uma terceira chamada: o código resolve as duas
e a fila termina aprovando.

```
Formato: SUCCESS_WITH_RESULT, JSON extraído da cerca de código
Regra: SUCCESS_WITH_RESULT, prioridade normalizada de 'Alta' para 'alta'
Dado sensível: SUCCESS_WITH_RESULT, nada encontrado. A resposta segue inteira
```

Compare o tempo das duas primeiras requisições: a 2 leva o tempo de duas chamadas, porque
foram duas chamadas. É o preço do reprompt, e ele aparece no relógio do usuário.

Na requisição **3**, o CPF que veio no chamado volta dentro do resumo. O terceiro guardrail
apaga o número e a resposta segue, sem nova chamada:

```
Formato: SUCCESS_WITH_RESULT, JSON extraído da cerca de código
Regra: OK, prioridade dentro do conjunto
Dado sensível: SUCCESS_WITH_RESULT, CPF mascarado. A resposta segue
```

Na requisição **4**, com o limite em `0`, não existe tentativa extra. A primeira reprovação
encerra a requisição, o framework lança a exceção e quem a recebe é o mapper da aplicação:

```
Formato: REPROMPT, resposta fora do formato JSON
Resposta reprovada na saída: Output validation failed. The guardrails have reached the maximum number of retries.
Guardrail messages:

A resposta não é um JSON de triagem.
```

O usuário recebe `502` com a frase da aplicação, e o texto em prosa que o modelo devolveu não
aparece em lugar nenhum da resposta. Esse é o contrato: reprovada é reprovada.

Repare também que o tempo de resposta varia de uma execução para outra, mesmo na mesma
requisição. É o modelo, não o guardrail: a validação acontece em milissegundos.

## Estrutura

```
src/main/java/com/eldermoraes/
  ai/TriagemDeChamados.java         # o AI Service e a anotação que declara a fila
  ai/Triagem.java                   # o formato que a aplicação lê: alvo do parse
  guardrails/FormatoGuardrail.java          # ① vazia (retry), formato e campo (reprompt)
  guardrails/RegraDeNegocioGuardrail.java   # ② prioridade: normaliza ou pede reprompt
  guardrails/DadoSensivelGuardrail.java     # ③ CPF mascarado pelo código
  rest/TriagemResource.java         # endpoint + o fallback que o usuário lê
```

O AI Service tem dois métodos com a mesma fila e system messages diferentes: `triar` pede o
formato, `triarSemFormato` não pede. É a diferença entre a requisição 1 e a 2, e ela mostra
uma coisa que vale anotar: pedir o formato no prompt reduz o erro de formato, e não elimina.
Na requisição 3 o modelo devolveu o JSON dentro de uma cerca de código mesmo tendo recebido a
instrução de responder só com o JSON.

O endpoint recebe e devolve `String` em `text/plain`. O `record Triagem` não é o corpo da
resposta: ele existe porque os guardrails precisam ler os campos para validá-los.

## Posição na escala de determinismo

Guardrail de saída fica num lugar particular da escala, e vale separar as duas metades dele.

**A validação é determinística.** Desserializar, comparar com uma lista, casar uma expressão
regular: mesma resposta, mesma decisão, sempre. É por isso que os testes dos três guardrails
rodam sem Ollama e dão o mesmo resultado todas as vezes.

**A reação reintroduz o modelo.** Retry e reprompt fazem uma pergunta nova, e a resposta dela
é tão incerta quanto a primeira. Pode passar na segunda, como na requisição 2, e pode não
passar. O guardrail não torna o sistema determinístico: ele garante que o que não serve não
passa, e o preço disso é uma chamada a mais quando ele decide insistir.

Reescrever é a exceção: ali a correção é inteiramente do código, e o resultado é tão previsível
quanto a validação.

Há também o que a regra de código não alcança. Resposta errada com formato certo passa por
todos os três: uma prioridade plausível mas equivocada, um resumo que inventa um detalhe, um
tom fora da política da empresa. Avaliar isso pede um modelo julgando a resposta, que é outro
guardrail e outro custo.

## Custo × risco

| | Custo | O que evita | Erra? |
|---|---|---|---|
| ① Formato | Uma desserialização | Resposta que a aplicação não consegue ler | não |
| ② Regra de negócio | Uma comparação com três valores | Formato certo com valor que a fila de atendimento não conhece | não |
| ③ Dado sensível | Uma expressão regular | CPF voltando na resposta e sendo gravado | não |
| Tentativa extra (retry, reprompt) | **Uma chamada inteira ao modelo** | A reprovação virando erro para o usuário | **sim** |

As três primeiras linhas somadas custam frações de milissegundo. A quarta é de outra ordem de
grandeza: é uma pergunta completa, com token e latência de pergunta completa, e o usuário
espera por ela. E ela pode não resolver nada, porque a segunda resposta também pode ser
reprovada.

Daí as duas decisões que este projeto toma e que você deveria tomar de novo no seu caso:
limite baixo e fallback claro. Duas chamadas no máximo, e uma frase honesta com triagem manual
quando elas não bastam. Insistir mais custa mais e não garante nada.

Guardrail é decisão de negócio, caso a caso. Mascarar o CPF na resposta depende do que a sua
empresa aceita gravar; normalizar a prioridade no código em vez de pedir correção ao modelo
depende de quanto você confia na sua lista de valores.

## Ficha técnica

- **API de guardrails**: `dev.langchain4j.guardrail` (interface `OutputGuardrail`, resultados
  `success` / `successWith` / `failure` / `fatal` / `retry` / `reprompt`) e a anotação
  `dev.langchain4j.service.guardrail.OutputGuardrails`. O método de validação recebe um
  `AiMessage` e devolve um `OutputGuardrailResult`.
- **Ordem de execução**: é a ordem das classes na anotação. Quando um guardrail pede retry ou
  reprompt, a fila inteira roda de novo sobre a nova resposta.
- **Limite de tentativas**: no Quarkus a chave é `quarkus.langchain4j.guardrails.max-retries`,
  e é ela que manda neste projeto. O valor é o número máximo de chamadas ao modelo por
  requisição: `2` é a chamada original mais uma tentativa, e `0` desliga a tentativa extra. Foi
  o que as requisições 2 e 4 mostraram no log. A anotação também aceita `maxRetries`, por
  método.
- **Quem esgota, lança**: acabadas as tentativas, o framework lança
  `OutputGuardrailException` com os motivos acumulados. O `@ServerExceptionMapper` do
  `TriagemResource` transforma isso na frase que o usuário lê.
- **A resposta aprovada é a que o último resultado carrega**: uma reescrita no meio da fila
  vale para os guardrails seguintes, e é o resultado do último guardrail que define o texto
  entregue à aplicação. Por isso o `DadoSensivelGuardrail`, que fecha a fila, devolve sempre a
  resposta que aprovou, mascarada ou não.
- **`failure` não pede nova chamada**: só `retry` e `reprompt` fazem isso. Um `failure`
  acumula o motivo, deixa a fila seguir e termina em exceção, e `fatal` encerra a fila na hora.
- **Streaming**: o guardrail só avalia a resposta inteira. Num método que devolve stream, a
  extensão acumula a resposta toda antes de validar, e roda os guardrails com um executor que
  não refaz a chamada: sem retry e sem reprompt. Ou seja, com guardrail de saída o streaming
  deixa de existir para o usuário. O endpoint deste projeto é síncrono por isso.
- **Guardrail pronto**: o framework traz um, `JsonExtractorOutputGuardrail`, que faz parte do
  que o `FormatoGuardrail` daqui faz. O projeto escreve o seu para deixar a mecânica visível:
  é a decisão e a reação que estão sendo ensinadas, não o extrator.
- **Testes**: `GuardrailsTest` cobre uma decisão por teste, sem subir o Quarkus e sem Ollama.
  `FilaDeSaidaTest` monta a fila declarada no AI Service com o laço de tentativas do framework
  e um executor de roteiro no lugar do modelo: prova que o reprompt leva a instrução na segunda
  chamada, que o retry não leva mensagem nova, que a reescrita da fila chega à aplicação e que
  o limite esgotado vira exceção sem devolver a resposta reprovada. `TriagemDeChamadosTest`
  sobe o Quarkus só para provar que a fila está montada no AI Service.
