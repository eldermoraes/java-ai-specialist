# Aula 02 (módulo Guardrails): HR Desk · guardrails de entrada

> - **Padrão**: três guardrails de entrada determinísticos, encadeados na frente de um AI Service
> - **Case**: pergunta para o assistente de RH entra → tamanho → escopo → dado sensível → só então o modelo
> - **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j · Ollama (`deepseek-v4-pro:cloud`)

---

## O que você vai aprender

Um guardrail de entrada é uma classe Java que roda **antes** da pergunta chegar ao modelo. Ele
recebe a mensagem do usuário, decide se ela passa, e essa decisão é dele — o modelo não é
consultado, e nenhum token é gasto até aqui.

Este projeto tem três, e a fila roda na ordem em que eles aparecem na anotação:

```
   PERGUNTA
      │
      ▼
 ┌──────────────────────────────┐
 │ ① TamanhoGuardrail           │  vazia → FATAL (a fila para aqui)
 │    conta caracteres          │  longa demais → FAILURE
 └──────────────┬───────────────┘
                ▼
 ┌──────────────────────────────┐
 │ ② EscopoGuardrail            │  assunto que não é de RH → FAILURE
 │    lista de temas            │
 └──────────────┬───────────────┘
                ▼
 ┌──────────────────────────────┐
 │ ③ DadoSensivelGuardrail      │  chave de API → FAILURE (bloqueia)
 │    expressão regular         │  CPF → SUCCESS_WITH_RESULT (mascara e segue)
 └──────────────┬───────────────┘
                ▼
             MODELO
```

Repare em duas coisas no desenho.

**A ordem não é decoração.** O primeiro da fila é o mais barato: contar caracteres. O último é o
mais caro: varrer o texto com expressão regular. Se a pergunta vai ser recusada de qualquer
jeito, é melhor que seja recusada logo.

**Reprovar tem dois graus.** `FAILURE` reprova e deixa os guardrails seguintes rodarem, para que
todos os motivos cheguem juntos à aplicação. `FATAL` interrompe a fila na hora — é o caso da
pergunta vazia, em que continuar avaliando não tem utilidade.

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
```

Em dev, o Swagger UI fica em <http://localhost:8080/q/swagger-ui> se você preferir disparar as
requisições pelo navegador.

## O que observar no log

Parte do seu estudo é ler o log. Cada guardrail registra a decisão que tomou e o motivo, então
dá para acompanhar a fila rodando:

- Na requisição **1**, os três dizem OK e em seguida aparece o request ao modelo.
- Na **2**, o tamanho reprova e o request ao modelo **não existe**. Nenhum token foi gasto.
- Na **3**, tamanho passa, escopo reprova. De novo, nenhum request ao modelo.
- Na **4**, o dado sensível mascara, e no request ao modelo o CPF aparece como `[CPF]`.

Compare o log dos guardrails com o log de requests: é a demonstração mais direta de que o
guardrail de entrada corta o custo antes dele existir.

A diferença entre `FAILURE` e `FATAL` fica visível aqui. Na requisição **3**, o escopo reprova e
mesmo assim o guardrail seguinte roda:

```
Tamanho: OK — 37 caracteres
Escopo: FAILURE — termo fora de escopo encontrado: 'eleição'
Dado sensível: OK — nenhum dado sensível encontrado
Requisição recusada na entrada: ... Esta pergunta está fora do escopo do assistente de RH.
```

Já com a pergunta em branco, a fila para no primeiro e os outros dois nem são chamados:

```
Tamanho: FATAL — pergunta vazia, a fila de guardrails para aqui
Requisição recusada na entrada: ... Pergunta vazia.
```

> No nome da classe dentro da mensagem de erro aparece um sufixo `_ClientProxy`. É o proxy que o
> CDI cria para o bean; o guardrail é a sua classe mesmo.

## Estrutura

```
src/main/java/com/eldermoraes/
  ai/AssistenteRh.java              # o AI Service e a anotação que declara a fila
  guardrails/TamanhoGuardrail.java  # ① vazia (fatal) e longa demais (failure)
  guardrails/EscopoGuardrail.java   # ② lista de temas fora de RH
  guardrails/DadoSensivelGuardrail.java  # ③ chave de API bloqueia, CPF mascara
  rest/AssistenteResource.java      # endpoint + a mensagem de recusa que o usuário lê
```

Quando um guardrail reprova, o framework lança uma exceção e a chamada não acontece. Quem
transforma isso na frase que o usuário lê é a aplicação, no `AssistenteResource`: o framework
decide se passa, você decide o que a pessoa vê.

## Posição na escala de determinismo

Este é o extremo determinístico da escala: **regra de código, sem modelo, sem token**. Mesma
pergunta, mesma decisão, sempre — e o teste em `GuardrailsTest` roda sem Ollama por causa disso.

O preço desse determinismo aparece no `EscopoGuardrail`: ele só pega o que está na lista. Quem
escrever a mesma pergunta com outras palavras passa direto. Cobrir o que não foi previsto exige
um classificador, que já é um componente não determinístico e já gasta token.

## Custo × risco

| | Custo | O que evita |
|---|---|---|
| Tamanho | Uma contagem de caracteres | Pergunta vazia e prompt gigante virando conta |
| Escopo | Uma varredura de lista | Assistente da empresa usado para outra coisa |
| Dado sensível | Duas expressões regulares | CPF e chave de API saindo da empresa no prompt |

Os três somados custam menos que uma única chamada ao modelo, e as três decisões acontecem
antes dela. É por isso que o guardrail de entrada é o mais barato do submódulo.

Guardrail é decisão de negócio, caso a caso. Nenhum dos três acima é obrigatório: bloquear ou
mascarar CPF, por exemplo, depende do que a sua empresa considera aceitável mandar para um
provedor externo.

## Ficha técnica

- **API de guardrails**: `dev.langchain4j.guardrail` (interface `InputGuardrail`, resultados
  `success` / `successWith` / `failure` / `fatal`) e a anotação
  `dev.langchain4j.service.guardrail.InputGuardrails`. A API nasceu na extensão do Quarkus e
  depois subiu para o core do LangChain4j, que é onde ela vive hoje.
- **Ordem de execução**: é a ordem das classes na anotação.
- **Sem streaming**: o endpoint é síncrono. Guardrail de saída só avalia a resposta inteira, e
  com streaming o usuário lê o texto antes da validação terminar.
- **Testes**: `GuardrailsTest` cobre as decisões dos três guardrails sem subir o Quarkus;
  `AssistenteRhTest` sobe o Quarkus só para provar que a fila está montada no AI Service.
