# Aula 7 (MCP): order-hub-live · seu MCP server no mundo: proteger, publicar e o que vem aí

> - **Módulo**: MCP · **Foco**: levar o MCP *server* para o mundo (proteger, publicar, futuro do protocolo)
> - **Case**: a mesma **Central de Pedidos da Cloud For You** da aula 6 (buscar, listar e cancelar pedidos), agora preparada para produção
> - **Stack**: Quarkus 3.35.2 · Java 25 · `quarkus-mcp-server-http` **1.13.1** (Quarkiverse) · `quarkus-oidc` (a autorização) · no client: LangChain4j via `quarkus-langchain4j-bom` + Ollama + `quarkus-oidc-client` (o token do perfil seguro)

Este projeto **parte do estado final da aula 6** ([`order-hub`](../../aula06/order-hub)), com **mesmo domínio e mesmas três tools** (`buscar_pedido`, `listar_pedidos_por_cliente`, `cancelar_pedido`), e o **prepara para o mundo**. É o padrão de aulas sequenciais do curso: cada aula parte do estado final da anterior. O shell original nasceu do `quarkus_create` na aula 6, então este projeto é uma **cópia derivada** daquele (não um projeto novo do zero), preservando essa base e acrescentando a ela a autorização OAuth, o manifesto de publicação e o olhar sobre a virada stateless do protocolo.

```
order-hub-live/                   ← POM pai agregador (sem código)
├── order-hub-live-server/        ← o MCP server (quem expõe as tools; sem LLM) + OIDC
├── order-hub-live-client/        ← o client declarativo (quem consome o server; com LLM)
└── server.json                   ← manifesto de publicação no registry (referência; não publicado)
```

---

## O que você vai aprender

Na aula 6 você saiu de consumidor e virou provedor: construiu o server, deu a ele resultado tipado e confirmação antes do destrutivo, e o testou até vê-lo funcionar de ponta a ponta, tudo no conforto do seu localhost. Esta aula trata do que separa **"funciona na minha máquina"** de **"está pronto para o mundo"**.

- **Proteger** com autorização: o server vira um **OAuth 2.1 Resource Server** que valida o Bearer token que chega no `/mcp`. É o espelho exato da autenticação que você configurou do lado client. E o espelho se completa aqui mesmo: no perfil seguro, o client obtém o token e o apresenta, fechando o ciclo `401 → token → 200` na sua máquina.
- **Publicar**, entrando no **registry oficial** do protocolo: escrever o `server.json`, entender a verificação de namespace e a CLI `mcp-publisher`. O walkthrough completo, **sem publicar** um exercício de localhost.
- **A virada stateless**: o **RC da spec (28/07/2026)** remove sessões e o handshake `initialize`; entender o que muda (a mecânica da conexão) e o que não muda (o modelo mental), e por que suas tools já estão prontas.
- **Estudo de caso**: o **Quarkus Agent MCP**, um MCP server real, open source, feito por quem faz o Quarkus, que amarra na prática tudo o que você aprendeu (e um contraste de transporte que ensina).

---

## Como rodar

Pré-requisitos: **Java 25**, **Maven 3.9+** (ou o wrapper `./mvnw`), **Docker/Podman** (para os Keycloak Dev Services no perfil seguro) e, para o client de verdade, **Ollama** em `localhost:11434` com `deepseek-v4-pro:cloud`.

### O server, perfil default (aberto)

```bash
# na raiz do order-hub-live
./mvnw -pl order-hub-live-server quarkus:dev
```

Sobe em `http://localhost:8080`, com o endpoint MCP em `http://localhost:8080/mcp`. No default, OIDC e os Keycloak Dev Services ficam **desligados**: nenhum container sobe sem você pedir.

### O server, perfil seguro (com OIDC)

```bash
./mvnw -pl order-hub-live-server quarkus:dev -Dquarkus.profile=seguro
```

Agora o `/mcp` exige um **Bearer token**: sem token, **`401`**. O Quarkus sobe um **Keycloak em container** (Dev Services) com realm e client prontos, fixo em `http://localhost:8180` (porta fixa para o client saber onde buscar o token): você vê a proteção funcionando sem montar infraestrutura à mão.

### O client, fechando o loop (perfil default)

Em **outro terminal**, com o server default de pé:

```bash
./mvnw -pl order-hub-live-client quarkus:dev
```

Sobe em `http://localhost:8081`. Abra `http://localhost:8081/` e pergunte "qual o status do pedido PED-1001?". O agente descobre as tools do **seu** server e as chama.

> **No perfil `seguro`, o client declarativo sem token não passa**, e isso é o comportamento **esperado**: é a proteção OIDC funcionando, e o chat avisa na tela. Antes de acionar o modelo, o endpoint pré-checa o MCP server (um `listTools()` direto no client MCP) e, se a chamada volta `401`, responde explicando a recusa. Sem essa pré-checagem, a falha seria silenciosa: o tool provider do LangChain4j degrada com um WARN no log e oferece o modelo **sem tools**, e o assistente "promete" consultar sem nunca executar nada. Para ver o outro lado do espelho, suba o client também no perfil `seguro` (abaixo): ele passa a apresentar o token, e a mesma pergunta volta a ser respondida.

### O client, perfil seguro (o ciclo 401 → token → 200)

Com o **server no perfil seguro** de pé:

```bash
./mvnw -pl order-hub-live-client quarkus:dev -Dquarkus.profile=seguro
```

Agora o client **obtém um token** no mesmo Keycloak que protege o server (um `OidcClient` com grant `client_credentials`: a aplicação se autentica como ela mesma, sem usuário) e o **apresenta** em cada chamada ao `/mcp`, via a SPI `McpClientAuthProvider` (`AutorizacaoCentralPedidos`). Pergunte de novo pelo pedido: a resposta volta com os dados. É o ciclo completo, `401` sem token, `200` com ele, rodando inteiro na sua máquina.

---

## Estrutura

```
order-hub-live/
├── pom.xml                                   # POM pai agregador (sem herança; agregação pura)
├── server.json                               # manifesto do registry (referência; não publicado)
├── order-hub-live-server/                    # ── o MCP server ──
│   ├── pom.xml                               # BOM io.quarkiverse.mcp 1.13.1; quarkus-oidc; sem LLM
│   └── src/
│       ├── main/java/com/eldermoraes/
│       │   ├── dto/
│       │   │   ├── Pedido.java               # record → structuredContent de buscar_pedido
│       │   │   └── PedidosDoCliente.java     # wrapper (topo do structuredContent é sempre objeto)
│       │   ├── dominio/
│       │   │   └── PedidoRepository.java     # fake em memória (ConcurrentHashMap = stateless/thread-safe)
│       │   └── mcp/
│       │       └── OrderHubMcp.java           # as 3 tools: buscar/listar/cancelar
│       ├── main/resources/
│       │   └── application.properties        # traffic-logging; sem modelo; perfil %seguro (OIDC)
│       └── test/java/com/eldermoraes/mcp/
│           └── OrderHubMcpTest.java           # JSON-RPC cru contra /mcp (perfil de teste sem Keycloak)
└── order-hub-live-client/                     # ── o client declarativo ──
    ├── pom.xml                               # quarkus-langchain4j-mcp + ollama + quarkus-oidc-client
    └── src/
        ├── main/java/com/eldermoraes/
        │   ├── ai/AssistentePedidos.java     # @RegisterAiService + @McpToolBox("central-pedidos")
        │   ├── ai/AutorizacaoCentralPedidos.java  # McpClientAuthProvider: apresenta o Bearer (perfil %seguro)
        │   └── rest/AssistenteResource.java  # POST /api/assistente; pré-checa o MCP server e explica o 401 na tela
        ├── main/resources/
        │   ├── application.properties        # porta 8081; modelos Ollama; bloco mcp.central-pedidos.*; %seguro (OidcClient)
        │   └── META-INF/resources/index.html # chat da Central de Pedidos
        └── test/java/com/eldermoraes/ai/
            └── AssistentePedidosTest.java     # smoke de montagem (passa sem Ollama/server) + IT @Disabled
```

---

## Pontos-chave

### 1. OIDC Resource Server: valida, não emite (o espelho do lado client)

Do lado client, você configurou autenticação: o agente **apresentava** um token OAuth. Agora você está do outro lado: o server **recebe** o token e o **valida**. Na terminologia do OAuth 2.1 (que a spec adota), o MCP server é um **Resource Server**: ele **não emite token nenhum**, só valida o Bearer que chega no cabeçalho. Quem emite é o **authorization server**, outro ator: o seu server confia nele, mas não faz o papel dele.

No Quarkus isso é `quarkus-oidc` + a política de path padrão do HTTP (não há config dedicada da extensão de server; a própria doc recomenda esse caminho):

```properties
%seguro.quarkus.http.auth.permission.mcp-endpoints.paths=/mcp,/mcp/*   # o path exato e os subcaminhos
%seguro.quarkus.http.auth.permission.mcp-endpoints.policy=authenticated
```

E o lado de quem chama, no perfil `seguro` do client: um `OidcClient` busca o token no Keycloak (grant `client_credentials`) e a SPI `McpClientAuthProvider` o apresenta em cada chamada ao `/mcp` (a classe `AutorizacaoCentralPedidos`, um bean de dez linhas). Emissor, portador e validador: o circuito OAuth inteiro rodando na sua máquina.

### 2. RFC 9728 (discovery de metadados) + RFC 8707 (audience do token)

Dois RFCs sustentam o desenho de confiança:

- **RFC 9728** (Protected Resource Metadata) = **discovery**: o server publica metadados dizendo "quem me protege é tal authorization server", para o client saber onde ir buscar um token. Materializado por `%seguro.quarkus.oidc.resource-metadata.enabled=true`.
- **RFC 8707** (Resource Indicators) = **audience**: amarra o token ao server de destino, para que um token emitido para o seu server não seja reusado em outro. No Quarkus é uma linha (`quarkus.oidc.token.audience=order-hub-live-server`), que este exercício **não usa** de propósito: o token que o Keycloak dos Dev Services emite não carrega audience nenhum, e exigir a validação derrubaria todo o fluxo com `401`, mesmo com token válido. Ative e ajuste com seu authorization server real.

Discovery e audience: duas peças pequenas que fecham o modelo de confiança.

### 3. Keycloak Dev Services: a infra que sobe sozinha

Para o hands-on rodar sem você subir um Keycloak à mão, o perfil `seguro` se apoia nos **Keycloak Dev Services**: o Quarkus sobe um Keycloak **em container automaticamente** no dev/test, já com realm e client provisionados. Você vê a proteção funcionando sem montar infraestrutura. No perfil default os Dev Services ficam **desligados** (`quarkus.keycloak.devservices.enabled=false`), então nada de container sobe no dia a dia.

### 4. `server.json` campo a campo, e "o registry indexa, não hospeda"

O manifesto de publicação, versionado na raiz como referência:

```json
{
  "name": "io.github.eldermoraes/order-hub",         // nome com namespace (verificado via GitHub)
  "description": "Expõe o sistema de pedidos ...",    // lida por quem procura no catálogo
  "version": "1.0.0",
  "remotes": [
    { "transport": "streamable-http", "url": "https://mcp.cloudforyou.example/mcp" }  // como alcançar
  ]
}
```

- **Namespace verificado.** A CLI **`mcp-publisher`** faz a verificação de namespace: para publicar sob `io.github.<usuário>`, você comprova a posse via GitHub.
- **O registry indexa, não hospeda.** Publicar **não sobe** o seu server para lugar nenhum: ele continua rodando onde você o rodou. Publicar é colocar a **descrição** dele no catálogo que clients consultam para descobrir servidores. É uma lista telefônica. E uma lista telefônica com um número que só toca na sua máquina não serve a ninguém.

**Walkthrough sem publicar.** A Central de Pedidos é um exercício em `localhost`; subir um brinquedo ao catálogo público só poluiria o índice para todo mundo. Fazemos o fluxo completo como demonstração (escrever o `server.json`, entender a verificação de namespace, conhecer a `mcp-publisher`), mas **não publicamos**. CTA honesto: publique quando tiver um server **real, hospedado, do seu domínio**, não um exercício. (O registry está em **preview** desde setembro/2025; trate o fluxo como o retrato de hoje, sujeito a evoluir.)

---

## O que observar no log

Ligue o traffic logging (já ligado: `quarkus.mcp.server.traffic-logging.enabled=true`) e leia o JSON-RPC do lado de quem **responde**:

| Mensagem JSON-RPC | O que significa |
|---|---|
| `initialize` → resposta com `capabilities` e `Mcp-Session-Id` | O handshake: o client abre a sessão e negocia capabilities (inclusive **elicitation**) |
| `notifications/initialized` | O client avisa que terminou de inicializar (o server responde `202`) |
| `tools/list` → `result.tools[]` | O server publica suas tools (nomes snake_case, descrições, `annotations`, `inputSchema`, `outputSchema`) |
| `tools/call` (`buscar_pedido`) → `result.structuredContent` | A tool foi invocada e devolveu **dados tipados** (não texto) |
| `tools/call` com id inexistente → `result.isError: true` | O erro de negócio foi transportado dentro da resposta (não como HTTP 500): o modelo lê e se recupera |
| `elicitation/create` → resposta do usuário | No cancelamento com client compatível: o server **pausa e pergunta**; a resposta traz `action` e o `content` |
| **`401 Unauthorized` no `/mcp` (perfil `seguro`, sem token)** | A **proteção OIDC funcionando**: sem Bearer válido, a chamada nem chega à tool |

---

## Roteiro de teste manual

1. **Ative o perfil seguro e veja o `401`.** Suba o server com `-Dquarkus.profile=seguro` (o Quarkus sobe o Keycloak em container). Chame `http://localhost:8080/mcp` **sem token** e veja o `401 Unauthorized` no log: a proteção OIDC funcionando (sem Bearer válido, a chamada nem chega à tool). Com o client de pé, abra o front (`http://localhost:8081/`) e pergunte por um pedido: a resposta no chat explica a recusa, a mesma proteção vista do lado de quem consome. Então feche o ciclo: suba o client com `-Dquarkus.profile=seguro` e repita a pergunta; agora ele apresenta o token e a resposta volta com os dados do pedido, o `401 → token → 200` completo.
2. **Derrube e reinicie o server, observando chamadas autossuficientes.** Suba, faça uma chamada, derrube o server, suba de novo e chame outra vez: cada chamada carrega tudo o que precisa nos argumentos, o comportamento stateless que a virada do dia 28 vai generalizar.

---

## Para experimentar

**Troque a Central de Pedidos pelo seu domínio.** A estrutura é a mesma para qualquer negócio: troque `Pedido`/`PedidoRepository` pelo **estoque da sua loja**, pela **sua coleção**, pelo **seu sistema de chamados**. Proteja o **seu** `/mcp` com o mesmo perfil OIDC, escreva o `server.json` do **seu** domínio e, quando ele for real e hospedado, publique-o no registry. O protocolo é aberto: qualquer agente compatível fala com o seu server, agora com o endpoint protegido.
