# Aula 08 (MCP): Context Cost · vendo a conta de contexto no log

> **Bloco**: MCP · **Foco**: o custo de contexto de plugar muitos servidores MCP
> **Case**: um agente **propositalmente superequipado** com 3 servidores MCP, montado
> para tornar duas falhas de custo **visíveis em log e número**
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j via `quarkus-langchain4j-bom` (nunca fixe versão) · Ollama (`deepseek-v4-pro:cloud`)

Este projeto demonstra o **problema**, não a solução. Ele existe para você **ver a conta subir**.

---

## O que você vai aprender

Nas aulas anteriores deste bloco você aprendeu a **conectar** servidores MCP: à mão, em
código (aula 03), e de forma **declarativa** pelo `application.properties` (aula 04). A
pergunta era sempre "como eu conecto?". Aqui a pergunta muda: **"quanto custa estar
conectado?"**.

A conta N+M do MCP mede **integração**. E, para integração, ela está certa: conectar
ficou barato. Mas ela tem uma letra miúda que só aparece **depois** de conectado, a cada
request. Esta aula torna essa letra miúda visível, através de **duas falhas**:

- **Falha 1: tool-definition overload.** A maioria dos clients MCP carrega **todas** as
  definições de tool para dentro do contexto, de uma vez. Como o modelo é *stateless*, o
  app **reenvia essas definições em todo request**, use-se ou não uma tool. Com três
  servidores é chato; com dezenas, os agentes "processam centenas de milhares de tokens
  antes de ler o pedido".
- **Falha 2: intermediate-result overload.** Todo **resultado intermediário passa pelo
  modelo**. Um `read_file` traz o conteúdo inteiro de um arquivo de volta ao modelo; para
  gravá-lo, o `write_file` **reenvia** esse mesmo conteúdo como argumento. O dado atravessa
  o modelo **duas vezes**. E o modelo nunca precisou *ler* o conteúdo para copiá-lo.

> Esta aula **não** reexplica token, janela de contexto ou TTFT: isso é chão conhecido do
> curso. O que muda aqui é a **escala** (e o preço dela), não o mecanismo.

---

## Como rodar

Pré-requisitos:

- **Ollama** em `localhost:11434` (Ollama Cloud) com `deepseek-v4-pro:cloud` disponível.
- **Node/npx** no PATH (o servidor de filesystem é um pacote npm que o `npx` baixa e roda).
- Acesso à internet (para o `npx` e para os servidores remotos DeepWiki e Context7).

```bash
cd modulo03/04_mcp/aula08/context-cost
./mvnw quarkus:dev
```

Abra <http://localhost:8080/> e experimente os cenários A, B e C abaixo. Ou via `curl`:

```bash
curl -s -X POST http://localhost:8080/api/assistente \
  -H 'Content-Type: application/json' \
  -d '{"pergunta":"Quanto é 2 + 2?"}' | jq
```

> **O interessante é o log, não o corpo da resposta.** Rode com `log-requests` /
> `log-responses` ligados (já estão) e acompanhe o request que o Quarkus envia ao modelo.

---

## Estrutura do código

```
context-cost/
├── pom.xml                                   # quarkus-langchain4j-ollama + quarkus-langchain4j-mcp (fiação declarativa)
└── src/main/
    ├── java/com/eldermoraes/
    │   ├── ai/
    │   │   └── AssistenteEquipado.java       # @RegisterAiService sem @Tool local; @McpToolBox({...}) declara os 3 servidores
    │   ├── medidor/
    │   │   └── MedidorDeContexto.java         # ⭐ no boot: conta tools/chars/tokens por servidor e imprime a tabela
    │   └── rest/
    │       └── AssistenteResource.java        # POST /api/assistente (@RunOnVirtualThread), mesmo shape da aula 03
    └── resources/
        ├── application.properties            # a fiação declarativa dos 3 clientes MCP (contraste com a aula 03)
        ├── dados/
        │   ├── relatorio-grande.md           # ~34 KB, o arquivo grande do cenário B
        │   ├── notas.md                       # arquivo pequeno
        │   └── resumo-vendas.md               # arquivo pequeno
        └── META-INF/resources/index.html      # chat simples
```

### Pontos-chave

#### 1. Fiação declarativa: o contraste com a aula 03

Na aula 03 a fiação foi **manual**: `StdioMcpTransport`, `DefaultMcpClient` e
`McpToolProvider` montados à mão, cada peça do protocolo visível no código. Aqui **não há
uma linha** de fiação Java. Os três clientes nascem só destas chaves:

```properties
quarkus.langchain4j.mcp.filesystem.transport-type=stdio
quarkus.langchain4j.mcp.filesystem.command=npx,-y,@modelcontextprotocol/server-filesystem,${user.dir}/src/main/resources/dados

quarkus.langchain4j.mcp.deepwiki.transport-type=streamable-http
quarkus.langchain4j.mcp.deepwiki.url=https://mcp.deepwiki.com/mcp

quarkus.langchain4j.mcp.context7.transport-type=streamable-http
quarkus.langchain4j.mcp.context7.url=https://mcp.context7.com/mcp
```

O foco desta aula é *quanto custa* estar conectado, não *como* conectar (você já sabe).

#### 2. O AI service superequipado, de propósito

```java
@RegisterAiService
public interface AssistenteEquipado {
    @McpToolBox({ "filesystem", "deepwiki", "context7" })   // <- os 3 servidores, pelo nome do properties
    @SystemMessage("Você é um assistente que responde usando as tools disponíveis ...")
    String perguntar(String pergunta);
}
```

Nenhum `@Tool` local: as capacidades vêm todas dos servidores MCP. O `@McpToolBox` declara
**quais** servidores este AI service enxerga: é o mesmo mecanismo de **subset de servidores
por AI service** citado na aula. Encolher essa lista é exatamente o **cenário C** abaixo.

#### 3. O medidor de boot: a tabela do custo

O `MedidorDeContexto` roda no *startup*, pega cada cliente MCP por CDI (qualifier
`@McpClientName("...")`), chama `listTools()` e imprime uma tabela:

```
servidor        | nº tools | chars defs | ~tokens (chars/4)
----------------+----------+------------+------------------
filesystem      |       14 |       6 200 |            1 550
deepwiki        |        2 |         900 |              225
context7        |        2 |       1 100 |              275
----------------+----------+------------+------------------
TOTAL           |       18 |       8 200 |            2 050
```

> Os números acima são **ilustrativos**: os reais aparecem no seu log e variam com a
> versão de cada servidor.

**Como ler a tabela:**

- **nº tools**: quantas tools o servidor expõe. Cada uma vira uma definição no contexto.
- **chars defs**: soma, por tool, de `nome + descrição + schema JSON dos parâmetros`. É o
  peso que aquele servidor acrescenta a **todo** request.
- **~tokens**: estimativa **chars ÷ 4**. **Disclaimer importante:** é uma aproximação de
  **ordem de grandeza**, **não** a contagem exata do tokenizador. Serve para ver o tamanho
  do problema, não para bater o número na casa.
- **TOTAL**: o que você paga, em toda pergunta, só por ter esses servidores plugados.

O medidor tem `try/catch` **por servidor**: se um remoto estiver fora do ar, a linha dele
sai como `indisponível` e o boot **não** cai. Ele também fica **desligado em teste**
(`%test.medidor.contexto.enabled=false`), porque medir exige conectar de fato (npx + rede).

---

## O que observar (a tabela de logs)

Rode em `quarkus:dev` e acompanhe:

| Observação no log | Explica… |
|---|---|
| No boot, a **tabela do MedidorDeContexto** com tools/chars/tokens por servidor | O custo fixo da falha 1, tornado número antes mesmo da 1ª pergunta |
| Numa pergunta **sem tool** ("quanto é 2 + 2?"), o request ao modelo já traz **todas** as definições dos 3 servidores | Falha 1: as definições viajam em todo request, use-se ou não uma tool |
| Numa cópia de arquivo, o conteúdo aparece **duas vezes** no log: no retorno do `read_file` e no argumento do `write_file` | Falha 2: o resultado intermediário passa pelo modelo na ida e na volta |
| Se um servidor remoto cair, a linha da tabela vira `indisponível` e a app sobe assim mesmo | `try/catch` por servidor + health dos clientes MCP desligado |

---

## Para experimentar

### Cenário A: a falha 1, na pergunta mais boba possível

Pergunte: **"quanto é 2 + 2?"**. Abra o request que o Quarkus enviou ao modelo e repare:
lá vão, empacotadas junto de uma conta de aritmética, **todas** as definições de tools dos
três servidores. Para uma pergunta que não usa **nenhuma** delas. Toda vez. Esse é o custo
fixo que a tabela do boot já tinha anunciado. Agora você o vê dentro do request.

### Cenário B: a falha 2, o conteúdo passando duas vezes

Peça: **"copie o conteúdo de `relatorio-grande.md` para `copia.md`"**. Acompanhe o log:

1. o `read_file` traz o conteúdo **inteiro** do relatório (~34 KB) de volta ao modelo;
2. para gravar, o modelo **reenvia esse conteúdo inteiro** como argumento do `write_file`.

Duas passagens completas do arquivo pelo modelo. E o modelo nunca precisou **ler** o
relatório para copiá-lo. É o clássico "office boy caro": carrega o papel de um lado para o
outro sem nunca precisar entender o que carrega.

> **Fallback** (se a escrita não estiver disponível no seu ambiente; ver "Revalidar" ao
> final): peça **"leia `relatorio-grande.md` e resuma cada seção"**. O conteúdo inteiro
> ainda passa pelo modelo **na ida**, demonstrando a falha 2 pela metade, e o roteiro se
> ajusta na fala.

### Cenário C: menos servidores, conta menor

Reduza o footprint e **compare**. Duas formas:

- comente dois dos três clientes no `application.properties` (ou ligue
  `%dev.quarkus.langchain4j.mcp.deepwiki.enabled=false`, idem context7); **ou**
- encolha o `@McpToolBox` do `AssistenteEquipado` para um nome só, ex.: `{"filesystem"}`.

Reinicie e olhe **a tabela do boot** (o TOTAL cai) e **o tamanho do request** no cenário A
(menos definições empacotadas). É a mitigação mais barata e mais ignorada: **conecte só o
que você usa.**

---

## Fora do escopo

Este projeto demonstra o **problema** de custo de contexto. Ele **não** implementa a
solução. Ficam como **conceito da aula**, sem código aqui:

- **Code execution with MCP**: o padrão em que o agente **escreve código** que roda num
  *sandbox* e chama as tools de lá, mantendo os resultados intermediários fora do contexto
  do modelo (mata a falha 2). Exige uma peça de infraestrutura (um sandbox seguro) que
  **hoje não tem caminho assentado em Quarkus/LangChain4j**; por isso é conceito, não
  hands-on. Os exemplos do artigo que descreve o padrão são em outra linguagem e aparecem
  na aula **só como citação**: você não roda nem recebe esse código.
- **MCP Fabric** e **MCP Gateway**: padrões operacionais para quando uma **empresa** tem
  dezenas de servidores MCP. Vocabulário de arquitetura para acompanhar, não receita para
  copiar.

---

## Revalidar antes da gravação

Convenção do bloco: esta superfície se move release a release. Antes de gravar, confirme:

- **(a)** a API vigente do **subset de servidores por AI service** (`@McpToolBox`) no
  `quarkus-langchain4j` da plataforma **3.35.x**: nome da anotação, pacote
  (`io.quarkiverse.langchain4j.mcp.runtime`) e formato do atributo (`String[]`).
- **(b)** que o **`@modelcontextprotocol/server-filesystem`** segue expondo **`write_file`**
  com escrita autorizada no diretório: o **cenário B** depende disso (senão, use o
  fallback do resumo).
- **(c)** o **status das betas** *Tool Search Tool* e *Programmatic Tool Calling* na Claude
  Developer Platform: são a produtização, na API da Claude, das ideias desta aula.
