# Aula 03 (MCP): Assistente de Projeto · consumindo servidores MCP no LangChain4j

> **Bloco**: MCP · **Foco**: MCP *client* no LangChain4j (montado em código)
> **Case**: um agente que responde perguntas sobre os arquivos de um projeto/repositório usando tools que **não estão no seu código**: vêm de servidores MCP externos
> **Stack**: Quarkus 3.35.2 · Java 25 · LangChain4j via `quarkus-langchain4j-bom` (nunca fixe versão) · Ollama (`deepseek-v4-pro:cloud`)

---

## O que você vai aprender

Nas aulas anteriores deste bloco vimos **por que** o MCP existe (conectividade padronizada, o "USB-C para IA") e **como** o protocolo se organiza (client–host–server, as primitivas Tools/Resources/Prompts, os transportes STDIO e Streamable HTTP). Agora é o **primeiro hands-on**: vamos ser o **lado client** e consumir servidores MCP prontos a partir de um agente LangChain4j.

O case é um **assistente de projeto**: um `@AiService` que responde perguntas sobre arquivos. A sacada é que ele **não sabe ler arquivos**: não há nenhum método `@Tool` no código dele. As capacidades chegam de fora, de dois servidores MCP:

- um **servidor de filesystem** rodando como processo local (transporte **STDIO**), que dá ao agente tools como `list_directory`, `read_file`, `directory_tree`;
- um **servidor remoto** na nuvem (transporte **Streamable HTTP**), o [DeepWiki](https://deepwiki.com), que responde perguntas sobre repositórios públicos do GitHub.

As três peças que você vai montar à mão:

| Peça | Classe LangChain4j | Papel |
|---|---|---|
| **Transporte** | `StdioMcpTransport` / `StreamableHttpMcpTransport` | como o cliente **conversa** com o servidor |
| **Cliente** | `DefaultMcpClient` (interface `McpClient`) | quem **fala o protocolo** MCP (JSON-RPC) |
| **Tool provider** | `McpToolProvider` | **adapta** o cliente para o contrato de tools do LangChain4j |

E o `@AiService` recebe esse provider pelo atributo `toolProviderSupplier` do `@RegisterAiService`.

> **Por que fazer isso em código, se dá para configurar por `application.properties`?**
> Porque a configuração declarativa (`quarkus.langchain4j.mcp.*`, extensão + Dev UI) é justamente o assunto da **próxima aula**. Fazendo à mão primeiro, cada peça do protocolo fica visível. E quando a versão declarativa aparecer, você vai saber exatamente o que ela está montando por você.

### O que é o "servidor de filesystem"?

É um servidor MCP de referência mantido pelo projeto oficial, distribuído como pacote npm
(`@modelcontextprotocol/server-filesystem`). Ele expõe, como **tools MCP**, operações de
leitura/navegação sobre **um diretório que você autoriza**, e só ele. Nós o subimos com
`npx`, e o Quarkus o mantém como um **processo filho**, conversando por stdin/stdout.

No projeto ele aponta, por padrão, para o **próprio diretório do projeto** (`${user.dir}`).
Por isso o assistente já consegue responder "quais arquivos existem aqui?". Mas o interesse
real é **apontar para um projeto do seu domínio**: troque `assistente.projeto.diretorio`
para o caminho do seu repositório e o mesmo agente passa a responder sobre o **seu** código.

## Como rodar

Pré-requisitos:

- **Ollama** em `localhost:11434` (Ollama Cloud) com o modelo `deepseek-v4-pro:cloud` disponível.
- **Node/npx** no PATH (o servidor de filesystem é um pacote npm que o `npx` baixa e executa).
- Acesso à internet (para o `npx` e para o servidor remoto DeepWiki).

```bash
cd modulo03/04_mcp/aula03/assistente-projeto
./mvnw quarkus:dev
```

Abra <http://localhost:8080/> e pergunte, por exemplo:

- *"Quais arquivos existem neste projeto e o que faz o README?"* → usa as tools de **filesystem**.
- *"No repositório quarkusio/quarkus, o que é o Quarkus?"* → usa a tool remota do **DeepWiki**.

Ou via `curl`:

```bash
curl -s -X POST http://localhost:8080/api/assistente \
  -H 'Content-Type: application/json' \
  -d '{"pergunta":"Quais arquivos .java existem e o que cada um faz?"}' | jq
```

> Para apontar o assistente para **outro** projeto, mude `assistente.projeto.diretorio`
> no `application.properties` (ou passe `-Dassistente.projeto.diretorio=/caminho/do/seu/projeto`).

## Estrutura do código

```
assistente-projeto/
├── pom.xml                                   # quarkus-langchain4j-ollama + quarkus-langchain4j-mcp (só as classes MCP)
└── src/main/
    ├── java/com/eldermoraes/
    │   ├── mcp/
    │   │   ├── McpClients.java               # ⭐ o coração: monta transporte + cliente dos 2 servidores
    │   │   └── ProjetoToolProviderSupplier.java  # adapta os McpClient num McpToolProvider
    │   ├── ai/
    │   │   └── AssistenteProjeto.java        # @AiService sem nenhum @Tool: as tools vêm do MCP
    │   └── rest/
    │       └── AssistenteResource.java       # POST /api/assistente (@RunOnVirtualThread)
    └── resources/
        ├── application.properties            # modelo + config própria (diretório + url remota)
        └── META-INF/resources/index.html     # chat simples
```

### Pontos-chave

#### 1. `McpClients`: trocou o transporte, o resto fica intacto

Os dois servidores são montados no mesmo lugar. Repare que a **única** diferença entre
eles é a linha do transporte:

```java
// BLOCO 1 (STDIO): o Quarkus sobe o servidor de filesystem como processo filho (npx)
McpTransport transporteLocal = new StdioMcpTransport.Builder()
        .command(List.of("npx", "-y", "@modelcontextprotocol/server-filesystem", diretorioProjeto))
        .logEvents(true)
        .build();
filesystem = abrir("filesystem", transporteLocal);

// BLOCO 2 (Streamable HTTP): mesmo cliente, só o transporte muda
McpTransport transporteRemoto = new StreamableHttpMcpTransport.Builder()
        .url(remotoUrl)          // https://mcp.deepwiki.com/mcp
        .logRequests(true).logResponses(true)
        .build();
remoto = abrir("remoto", transporteRemoto);
```

E o método que cria o cliente é **idêntico** para os dois:

```java
private McpClient abrir(String chave, McpTransport transporte) {
    return new DefaultMcpClient.Builder()
            .key(chave)
            .transport(transporte)   // <- a única coisa que diferencia local de remoto
            .build();
}
```

Essa é a mensagem central: **STDIO** (local, processo filho, ideal para servidores
distribuídos como npm/binário) e **Streamable HTTP** (remoto, servidor na nuvem) são
intercambiáveis. O cliente, o provider e o agente **não sabem nem se importam** com qual
transporte está por baixo.

O servidor remoto é opcional e resiliente: se o DeepWiki estiver fora do ar, um `try/catch`
registra o aviso e o assistente segue só com as tools de filesystem.

#### 2. `McpToolProvider`: um provider, vários servidores

```java
return McpToolProvider.builder()
        .mcpClients(clients.ativos())       // filesystem + remoto
        .failIfOneServerFails(false)        // remoto caiu? segue com o local
        .build();
```

O `McpToolProvider` é a ponte entre o mundo MCP e o `ToolProvider` do LangChain4j: a cada
pergunta, ele lista as tools dos servidores conectados e as oferece ao modelo. Um único
provider agrega **todos** os servidores.

#### 3. `@AiService` sem nenhum `@Tool`

```java
@RegisterAiService(toolProviderSupplier = ProjetoToolProviderSupplier.class)
public interface AssistenteProjeto {
    @SystemMessage("""
        Você é um assistente de projeto ...
        Para responder, use as tools disponíveis (fornecidas por servidores MCP) ...
        """)
    String perguntar(String pergunta);
}
```

Compare com o módulo de Agentes, onde as tools eram métodos `@Tool` **dentro** do seu
código. Aqui não há nenhum: o `toolProviderSupplier` aponta para um bean
`Supplier<ToolProvider>`, e as capacidades chegam de servidores externos. Adicionar,
remover ou trocar um servidor MCP **não muda uma linha** desta interface: é exatamente a
promessa de reutilização que o MCP faz.

## O que observar

Rode com `quarkus.langchain4j.log-requests=true` / `log-responses=true` (já ligados) e
acompanhe o log:

| Observação no log | Explica… |
|---|---|
| No boot: `Servidor MCP de filesystem conectado (stdio) sobre '...'` | O `@PostConstruct` de `McpClients` sobe o processo `npx` e faz o handshake `initialize` |
| No boot: `Servidor MCP remoto conectado (Streamable HTTP) em https://mcp.deepwiki.com/mcp` | O mesmo `DefaultMcpClient`, agora sobre `StreamableHttpMcpTransport` |
| Na 1ª pergunta, a request ao modelo já traz uma **lista de tools** (`list_directory`, `read_file`, `ask_question`, ...) | O `McpToolProvider` listou as tools dos servidores e as injetou no request |
| A resposta do modelo vem com um **tool call** (ex.: `read_file` com `{"path": "README.md"}`) | O modelo decidiu usar uma tool MCP em vez de responder direto |
| Uma **segunda** ida ao servidor MCP e o resultado voltando pro modelo | Ciclo tool-calling: pergunta → tool → resultado → resposta final |
| A resposta final cita **caminhos e conteúdo reais** dos arquivos | O agente respondeu com base no resultado da tool, não em "chute" |
| Pergunta sobre repositório do GitHub → tool `ask_question` (DeepWiki) em vez de `read_file` | O modelo escolhe o servidor certo pela descrição das tools |
| Derrube a internet e reinicie → `Servidor MCP remoto indisponível ... Seguindo apenas com filesystem` | Resiliência: `try/catch` no boot + `failIfOneServerFails(false)` no provider |

## STDIO × Streamable HTTP (quando usar cada um)

| | **STDIO** (bloco local) | **Streamable HTTP** (bloco remoto) |
|---|---|---|
| Onde o servidor roda | processo filho na sua máquina | serviço na rede/nuvem |
| Como distribui | binário / pacote npm / jar | URL |
| Autenticação | herdada do ambiente local | tipicamente OAuth (aqui: servidor público sem auth) |
| Bom para | ferramentas de sistema (filesystem, git, sqlite local) | APIs SaaS, serviços compartilhados entre times |
| Nesta aula | `@modelcontextprotocol/server-filesystem` via `npx` | DeepWiki (`https://mcp.deepwiki.com/mcp`) |

> **Nota (jul/2026):** *Streamable HTTP* é o transporte HTTP atual do MCP. O antigo
> *HTTP+SSE* é legado e está sendo depreciado: não use `HttpMcpTransport` (SSE) em
> projetos novos.

## Para experimentar

- **Aponte para o seu projeto:** mude `assistente.projeto.diretorio` para o caminho de um
  repositório seu e pergunte sobre o código dele. O agente é o mesmo; muda só o diretório
  que o servidor de filesystem enxerga.
- **Troque o servidor remoto:** troque `assistente.mcp.remoto.url` para
  `https://mcp.context7.com/mcp` (Context7, também sem auth, especializado em documentação
  de bibliotecas) e veja as tools mudarem, sem tocar no `@AiService`.
- **Desligue o remoto:** `assistente.mcp.remoto.habilitado=false` e confirme que o
  assistente continua respondendo sobre arquivos locais.
- **Espie o handshake:** com `logEvents(true)`/`logRequests(true)` já ligados, observe no
  log o `initialize` e o `tools/list` de cada servidor no boot.
- **Conte as chamadas:** faça uma pergunta que exija ler 2 arquivos e conte, no log, quantas
  idas ao servidor MCP o modelo fez até fechar a resposta.
