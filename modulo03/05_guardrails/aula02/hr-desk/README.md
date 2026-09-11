# Aula 02 (módulo Guardrails): HR Desk · guardrails de entrada

> - **Padrão**: quatro guardrails de entrada encadeados na frente de um AI Service, do mais determinístico ao menos
> - **Case**: pergunta para o assistente de RH entra → tamanho → escopo → dado sensível → classificador → só então o modelo
> - **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j · Ollama (`deepseek-v4-pro:cloud` + `gemma4:31b-cloud`)

---

## O que você vai aprender

Um guardrail de entrada é uma classe Java que roda **antes** da pergunta chegar ao modelo. Ele
recebe a mensagem do usuário e decide se ela passa. Os guardrails determinísticos fazem isso
sem consultar um modelo; o guardrail por sentido consulta o classificador antes do assistente.

Este projeto tem quatro, e a fila roda na ordem em que eles aparecem na anotação, do mais
barato ao mais caro:

```
   PERGUNTA
      │
      ▼
 ┌──────────────────────────────┐
 │ ① TamanhoGuardrail           │  vazia → FATAL (a fila para aqui)
 │    conta caracteres          │  longa demais → FATAL
 └──────────────┬───────────────┘
                ▼
 ┌──────────────────────────────┐
 │ ② EscopoGuardrail            │  assunto que não é de RH → FATAL
 │    lista de temas            │
 └──────────────┬───────────────┘
                ▼
 ┌──────────────────────────────┐
 │ ③ DadoSensivelGuardrail      │  chave de API → FATAL (bloqueia)
 │    expressão regular         │  CPF → SUCCESS_WITH_RESULT (mascara e segue)
 └──────────────┬───────────────┘
                ▼
 ┌──────────────────────────────┐
 │ ④ EscopoPorSentidoGuardrail  │  classificador diz FORA → FATAL
 │    pergunta a um modelo      │  ← o único não determinístico da fila
 └──────────────┬───────────────┘
                ▼
             MODELO
```

Os três primeiros são regra de código. O quarto consulta um modelo pequeno, e está ali por um
motivo específico: o `EscopoGuardrail` só enxerga os termos da lista dele. *"Me ensina a fazer um
bolo de cenoura?"* não tem nenhum termo listado e passa direto por ele. O classificador entende a
pergunta e recusa.

Essa diferença tem preço, e o preço é o assunto: o quarto guardrail gasta token, soma a latência
de uma chamada a mais e **erra**: pode barrar quem tinha o direito de perguntar. Os três
primeiros nunca fazem isso. Por isso ele vem por último: o que a regra de código já resolveu não
chega até ele. E repare que a pergunta chega ao classificador **já mascarada**: o CPF não vai
nem para o modelo pequeno.

Repare em duas coisas no desenho.

**A ordem não é decoração.** O primeiro da fila é o mais barato: contar caracteres. O último é o
mais caro: uma chamada a um modelo. Se a pergunta vai ser recusada de qualquer jeito, é melhor
que seja recusada logo, e de graça.

**Toda reprovação interrompe a fila.** Neste projeto, todos os guardrails usam `fatal()`
quando recusam uma pergunta. Assim, uma rejeição determinística impede a chamada ao
classificador e ao modelo principal. A aplicação recebe o motivo da primeira rejeição.

O framework também oferece `failure()`, que continua a fila para acumular motivos de
rejeição. Não usamos essa opção aqui, porque continuar até o classificador gastaria uma
chamada ao modelo numa pergunta já recusada.

E há um terceiro caminho, que não é passar nem reprovar: **mascarar**. O CPF é apagado da
mensagem e a pergunta segue reescrita. O modelo não precisa do número para responder sobre
férias, então o dado simplesmente não sai da empresa. Quem roda depois de um guardrail que
reescreve já recebe a versão reescrita.

## Como rodar

Pré-requisito: **Ollama** ativo (local ou Cloud) com o modelo configurado em
`application.properties`.

```bash
cd modulo03/05_guardrails/aula02/hr-desk
./mvnw quarkus:dev
```

O endpoint recebe e devolve texto puro:

```bash
# 1. passa: pergunta comum de RH
curl -X POST localhost:8080/rh -H 'Content-Type: text/plain' \
  -d 'Quantos dias de férias eu tenho acumulados?'

# 2. reprova no tamanho
curl -X POST localhost:8080/rh -H 'Content-Type: text/plain' \
  -d "$(printf 'a%.0s' {1..2500})"

# 3. reprova no escopo
curl -X POST localhost:8080/rh -H 'Content-Type: text/plain' \
  -d 'Em quem eu voto na eleição deste ano?'

# 4. passa mascarada: o CPF não chega ao modelo
curl -X POST localhost:8080/rh -H 'Content-Type: text/plain' \
  -d 'Confere as férias do CPF 123.456.789-00, por favor'

# 5. passa pela lista de termos e é recusada pelo classificador
curl -X POST localhost:8080/rh -H 'Content-Type: text/plain' \
  -d 'Me ensina a fazer um bolo de cenoura?'
```

Em dev, o Swagger UI fica em <http://localhost:8080/q/swagger-ui> se você preferir disparar as
requisições pelo navegador.

## O que observar no log

Parte do seu estudo é ler o log. Cada guardrail registra a decisão que tomou e o motivo, então
dá para acompanhar a fila rodando:

- Na requisição **1**, os quatro dizem OK e em seguida aparece o request ao modelo grande.
- Na **2**, o tamanho reprova e o request ao modelo **não existe**. Nenhum token foi gasto.
- Na **3**, tamanho passa, escopo reprova. De novo, nenhum request ao modelo.
- Na **4**, o dado sensível mascara, e no request ao modelo o CPF aparece como `[CPF]`.
- Na **5**, os três primeiros dizem OK e o classificador reprova. Aqui existe um request ao
  modelo pequeno (o token do guardrail), mas nenhum ao modelo grande.

Compare o log dos guardrails com o log de requests: é a demonstração mais direta de que o
guardrail de entrada corta o custo antes dele existir.

Na requisição **2**, somente o guardrail de tamanho roda. Na **3**, o tamanho passa e o
escopo interrompe a fila; os guardrails de dado sensível e de classificação nem são chamados:

```
Tamanho: OK, 37 caracteres
Escopo: FATAL, termo fora de escopo: 'eleição'
Requisição recusada na entrada: ... EscopoGuardrail ... está fora do escopo ...
```

Não há chamada ao Gemma nem ao modelo principal nesses dois casos.

Já com a pergunta em branco, a fila para no primeiro e os outros três nem são chamados:

```
Tamanho: FATAL, pergunta vazia. A fila de guardrails para aqui
Requisição recusada na entrada: ... Pergunta vazia.
```

Usamos `FATAL` em toda rejeição: quando a pergunta já foi recusada, pagar pelo resto da fila
é desperdício.

> No nome da classe dentro da mensagem de erro aparece um sufixo `_ClientProxy`. É o proxy que o
> CDI cria para o bean; o guardrail é a sua classe mesmo.

## Estrutura

```
src/main/java/com/eldermoraes/
  ai/AssistenteRh.java              # o AI Service e a anotação que declara a fila
  ai/ClassificadorDeEscopo.java     # o modelo pequeno que o guardrail ④ consulta
  guardrails/TamanhoGuardrail.java  # ① vazia (fatal) e longa demais (fatal)
  guardrails/EscopoGuardrail.java   # ② lista de temas fora de RH
  guardrails/DadoSensivelGuardrail.java     # ③ chave de API bloqueia, CPF mascara
  guardrails/EscopoPorSentidoGuardrail.java # ④ pergunta ao classificador
  rest/AssistenteResource.java      # endpoint + a mensagem de recusa que o usuário lê
```

Quando um guardrail reprova, o framework lança uma exceção e a chamada ao modelo principal não acontece. Quem
transforma isso na frase que o usuário lê é a aplicação, no `AssistenteResource`: o framework
decide se passa, você decide o que a pessoa vê.

## Posição na escala de determinismo

A fila deste projeto percorre a escala inteira, e é por isso que ela tem quatro elementos e não
três.

Os guardrails ① a ③ são o extremo determinístico: **regra de código, sem modelo, sem token**.
Mesma pergunta, mesma decisão, sempre. É por causa disso que os testes deles rodam sem Ollama.

O guardrail ④ é o outro extremo: quem decide é um modelo. Ele alcança o que a regra de código
não alcança, e paga por isso em token, em latência e em confiabilidade: o classificador erra.
Note que nem o teste dele escapa disso: `GuardrailsTest` testa esse guardrail com um dublê do
classificador, porque testar contra o modelo de verdade daria um teste que às vezes passa e às
vezes não.

Na prática os dois se empilham: o determinístico na frente, porque é barato e não erra; o modelo
atrás, para o que sobrou.

## Custo × risco

| | Custo | O que evita | Erra? |
|---|---|---|---|
| ① Tamanho | Uma contagem de caracteres | Pergunta vazia e prompt gigante virando conta | não |
| ② Escopo | Uma varredura de lista | Assistente da empresa usado para outra coisa | não |
| ③ Dado sensível | Duas expressões regulares | CPF e chave de API saindo no prompt | não |
| ④ Escopo por sentido | Uma chamada a um modelo pequeno | O que a lista de termos não previu | **sim** |

Os três primeiros somados custam menos que uma única chamada ao modelo, e as três decisões
acontecem antes dela. O quarto já é outra conversa: ele custa uma chamada, e essa chamada
acontece em toda pergunta que chegou até ele, inclusive nas que iam ser respondidas normalmente.

Guardrail é decisão de negócio, caso a caso. Nenhum dos quatro acima é obrigatório. Bloquear ou
mascarar CPF depende do que a sua empresa considera aceitável mandar para um provedor externo. E
vale perguntar, no seu caso, se o classificador compensa: ele cobre mais, mas cobra por pergunta
e às vezes recusa quem não devia.

## Ficha técnica

- **API de guardrails**: `dev.langchain4j.guardrail` (interface `InputGuardrail`, resultados
  `success` / `successWith` / `failure` / `fatal`) e a anotação
  `dev.langchain4j.service.guardrail.InputGuardrails`. A API nasceu na extensão do Quarkus e
  depois subiu para o core do LangChain4j, que é onde ela vive hoje.
- **Ordem de execução**: é a ordem das classes na anotação.
- **Sem streaming**: o endpoint é síncrono. Guardrail de saída só avalia a resposta inteira, e
  com streaming o usuário lê o texto antes da validação terminar.
- **Modelos**: o assistente responde no modelo grande; o classificador do guardrail ④ usa o
  modelo `smaller`, selecionado com `@RegisterAiService(modelName = "smaller")`.
- **Testes**: `GuardrailsTest` cobre as decisões dos quatro guardrails sem subir o Quarkus e sem
  Ollama: o classificador entra como dublê, injetado pelo construtor do guardrail;
  `AssistenteRhTest` sobe o Quarkus só para provar que a fila está montada no AI Service.
