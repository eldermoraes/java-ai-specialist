# Aula 7 (MCP): order-hub-live · seu MCP server no mundo: proteger, publicar e o que vem aí

> **Bloco**: MCP · **Foco**: levar o MCP *server* para o mundo (proteger, publicar, futuro do protocolo)
> **Case**: a mesma **Central de Pedidos da Cloud For You** da aula 6 (buscar, listar e cancelar pedidos), agora preparada para produção
> **Stack**: Quarkus 3.35.2 · Java 25 · `quarkus-mcp-server-http` **1.13.1** (Quarkiverse) · `quarkus-oidc` (a fechadura) · no client: LangChain4j via `quarkus-langchain4j-bom` + Ollama

Este projeto **parte do estado final da aula 6** ([`order-hub`](../../aula06/order-hub)), com **mesmo domínio e mesmas três tools** (`buscar_pedido`, `listar_pedidos_por_cliente`, `cancelar_pedido`), e o **prepara para o mundo**. É o padrão de aulas sequenciais do curso: cada aula parte do estado final da anterior. O shell original nasceu do `quarkus_create` na aula 6, então este projeto é uma **cópia derivada** daquele (não um projeto novo do zero), preservando essa base e acrescentando a ela a fechadura, o manifesto de publicação e o olhar sobre a virada stateless do protocolo.

```
order-hub-live/                   ← POM pai agregador (sem código)
├── order-hub-live-server/        ← o MCP server (quem expõe as tools; sem LLM) + OIDC
├── order-hub-live-client/        ← o client declarativo (quem consome o server; com LLM)
└── server.json                   ← manifesto de publicação no registry (referência; não publicado)
```

---

## O que você vai aprender

Na aula 6 você saiu de consumidor e virou provedor: construiu o server, deu a ele resultado tipado e confirmação antes do destrutivo, e o testou até vê-lo funcionar de ponta a ponta, tudo no conforto do seu localhost. Esta aula trata do que separa **"funciona na minha máquina"** de **"está pronto para o mundo"**.

- **Proteger**, trancando a porta: o server vira um **OAuth 2.1 Resource Server** que valida o Bearer token que chega no `/mcp`. É o espelho exato da autenticação que você configurou do lado client.
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

### O server, perfil seguro (a fechadura)

```bash
./mvnw -pl order-hub-live-server quarkus:dev -Dquarkus.profile=seguro
```

Agora o `/mcp` exige um **Bearer token**: sem token, **`401`**. O Quarkus sobe um **Keycloak em container** (Dev Services) com realm e client prontos: você vê a fechadura funcionando sem montar infraestrutura à mão.

### O client, fechando o loop (perfil default)

Em **outro terminal**, com o server default de pé:

```bash
./mvnw -pl order-hub-live-client quarkus:dev
```

Sobe em `http://localhost:8081`. Abra `http://localhost:8081/` e pergunte "qual o status do pedido PED-1001?". O agente descobre as tools do **seu** server e as chama.

> **No perfil `seguro`, o client declarativo sem token não passa**, e isso é o comportamento **esperado**: é a fechadura funcionando. O circuito completo de autenticação do lado client (apresentar um token OAuth ao consumir o server) fica para a gravação / a aula de client auth. Aqui o client fecha o loop no perfil default, e o perfil `seguro` demonstra o `401`.

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
    ├── pom.xml                               # quarkus-langchain4j-mcp + ollama
    └── src/
        ├── main/java/com/eldermoraes/
        │   ├── ai/AssistentePedidos.java     # @RegisterAiService + @McpToolBox("central-pedidos")
        │   └── rest/AssistenteResource.java  # POST /api/assistente (@RunOnVirtualThread)
        ├── main/resources/
        │   ├── application.properties        # porta 8081; modelos Ollama; bloco mcp.central-pedidos.*
        │   └── META-INF/resources/index.html # chat da Central de Pedidos
        └── test/java/com/eldermoraes/ai/
            └── AssistentePedidosTest.java     # smoke de wiring (passa sem Ollama/server) + IT @Disabled
```

> **Nota de versão: o BOM 1.13.1 (herdado da aula 6).** A plataforma Quarkus 3.35.2 alinha o `quarkus-mcp-server` na 1.12.0; o pom do server sobrepõe isso importando o BOM `io.quarkiverse.mcp:quarkus-mcp-server-bom:1.13.1`, um override deliberado (a regra "nunca fixar versão" vale para o LangChain4j, não para este BOM). A 1.13.0 (04/06/2026) foi o marco em que o server Java passou a falar Streamable HTTP e ganhou o suporte inicial ao protocolo stateless do RC: exatamente os temas desta aula.

---

## Pontos-chave

### 1. OIDC Resource Server: valida, não emite (o espelho do lado client)

Do lado client, você configurou autenticação: o agente **apresentava** um token OAuth. Agora você está do outro lado: o server **recebe** o token e o **valida**. Na terminologia do OAuth 2.1 (que a spec adota), o MCP server é um **Resource Server**: ele **não emite token nenhum**, só valida o Bearer que chega no cabeçalho. Quem emite é o **authorization server**, outro ator: o seu server confia nele, mas não faz o papel dele.

No Quarkus isso é `quarkus-oidc` + a política de path padrão do HTTP (não há config dedicada da extensão de server; a própria doc recomenda esse caminho):

```properties
%seguro.quarkus.http.auth.permission.mcp-endpoints.paths=/mcp,/mcp/*   # o path exato e os subcaminhos
%seguro.quarkus.http.auth.permission.mcp-endpoints.policy=authenticated
```

**Onde esta aula termina:** a fechadura é a autorização **essencial**, indispensável, mas não é a história completa de segurança de MCP. Tool poisoning, rug pull, o OWASP MCP Top 10, o `mcp-scan`, CVEs específicos são assunto de uma parte dedicada, mais adiante no bloco. Aqui é a fechadura; lá, o estudo das ameaças e defesas, partindo do que você trancou aqui.

### 2. RFC 9728 "cartaz na porta" + RFC 8707 "token com destinatário"

Dois RFCs sustentam o desenho de confiança:

- **RFC 9728** (Protected Resource Metadata) = o **"cartaz na porta"**: o server publica metadados dizendo "quem me protege é tal authorization server", para o client saber onde ir buscar um token. Materializado por `%seguro.quarkus.oidc.resource-metadata.enabled=true`.
- **RFC 8707** (Resource Indicators) = o **"token com destinatário"**: amarra o token ao server de destino, para que um token emitido para o seu server não seja reusado em outro. É a linha `%seguro.quarkus.oidc.token.audience=order-hub-live-server`, deixada **comentada** e marcada `[REVALIDAR]`: com os Keycloak Dev Services, o audience padrão emitido pode não bater e derrubar toda chamada com `401`. Ative e ajuste com seu authorization server real.

Cartaz na porta e destinatário no envelope: duas peças pequenas que fecham o modelo de confiança.

### 3. Keycloak Dev Services: a infra que sobe sozinha

Para o hands-on rodar sem você subir um Keycloak à mão, o perfil `seguro` se apoia nos **Keycloak Dev Services**: o Quarkus sobe um Keycloak **em container automaticamente** no dev/test, já com realm e client provisionados. Você vê a fechadura funcionando sem montar infraestrutura. No perfil default os Dev Services ficam **desligados** (`quarkus.keycloak.devservices.enabled=false`), então nada de container sobe no dia a dia.

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

### 5. A virada stateless: futuro iminente (hoje é 17/07/2026)

O **RC da spec (28/07/2026)** propõe remover sessões e o handshake `initialize`: cada chamada passa a ser autossuficiente, sem sessão por trás. Motivo: **escala** (múltiplas instâncias atrás de um load balancer, serverless). Na data deste material o RC **ainda não saiu** (sai em ~2 semanas): trate como **futuro iminente**, não fato consumado.

O que muda no que você aprendeu? **No conceito, nada**: tools, structured output, elicitation, autorização continuam valendo; muda a mecânica da conexão, não o modelo mental. O que você faz hoje: **tools autossuficientes e thread-safe**. As três tools já recebem tudo nos argumentos, e o `PedidoRepository` usa `ConcurrentHashMap`, porque um server remoto atende **N agentes ao mesmo tempo**: **tool stateless é também tool thread-safe**. A `quarkus-mcp-server-http` 1.13.x já tem suporte inicial ao stateless, e a negociação de versão no handshake garante um período de convivência entre clients antigos e servers novos. Quem desenha stateless hoje não refaz nada quando a virada chegar.

### 6. Estudo de caso: Quarkus Agent MCP, um server real (e o contraste de transporte)

O **Quarkus Agent MCP** (github.com/quarkusio/quarkus-agent-mcp, Apache 2.0; blog de 07/mai/2026) resolve um problema elegante. Agentes de código (Claude Code, Copilot, Cursor) precisam criar e manter apps Quarkus. O Quarkus já tem um Dev MCP **embutido** no dev mode (`/q/dev-mcp`), mas ele **morre junto com o app**. E o agente mais precisa de ajuda justamente quando o app quebrou. A solução é o princípio **survive-the-crash**: o Agent MCP é um server **standalone**, que roda fora do processo observado: envolve o `quarkus dev` como processo filho e continua vivo quando o app quebra, deixando o agente ler a exceção estruturada e corrigir o código.

Três lições transferíveis direto para o **seu** server:

1. **Rodar fora do processo observado é um padrão de resiliência** (o survive-the-crash).
2. **A descrição de uma tool é a interface para o modelo**: o mesmo ponto da aula 6, aqui provado num projeto real.
3. **Respostas concisas importam**: a janela de contexto do agente é cara; cada token que a tool devolve compete por espaço.

E o **contraste de transporte** que fecha a ideia: a nossa Central de Pedidos é **HTTP remoto e stateless**; o Agent MCP é **STDIO local e stateful**, e precisa ser assim, porque gerencia um processo filho (e gerenciar processo é estado). Um não está mais certo que o outro: **transporte e desenho seguem o caso de uso, não a moda.** Você já tem o repertório para ler o código dele: é o mesmo protocolo que você domina.

---

## O que observar no log

Ligue o traffic logging (já ligado: `quarkus.mcp.server.traffic-logging.enabled=true`) e leia o JSON-RPC do lado de quem **responde**:

| Mensagem JSON-RPC | O que significa |
|---|---|
| `initialize` → resposta com `capabilities` e `Mcp-Session-Id` | O handshake: o client abre a sessão e negocia capabilities (inclusive **elicitation**) |
| `notifications/initialized` | O client avisa que terminou de inicializar (o server responde `202`) |
| `tools/list` → `result.tools[]` | O server publica suas tools (nomes snake_case, descrições, `annotations`, `inputSchema`, `outputSchema`) |
| `tools/call` (`buscar_pedido`) → `result.structuredContent` | A tool foi invocada e devolveu **dados tipados** (não texto) |
| `tools/call` com id inexistente → `result.isError: true` | O erro de negócio viajou dentro da resposta (não como HTTP 500): o modelo lê e se recupera |
| `elicitation/create` → resposta do usuário | No cancelamento com client compatível: o server **pausa e pergunta**; a resposta traz `action` e o `content` |
| **`401 Unauthorized` no `/mcp` (perfil `seguro`, sem token)** | A **fechadura OIDC funcionando**: sem Bearer válido, a chamada nem chega à tool |

---

## Roteiro de teste manual

1. **Ative o perfil seguro e veja o `401`.** Suba o server com `-Dquarkus.profile=seguro` (o Quarkus sobe o Keycloak em container). Chame `http://localhost:8080/mcp` **sem token** e veja o `401 Unauthorized` no log: a fechadura funcionando (sem Bearer válido, a chamada nem chega à tool).
2. **Walkthrough do `server.json` + `mcp-publisher`, sem publicar.** Percorra o manifesto campo a campo, entenda a verificação de namespace (`io.github.<usuário>` via GitHub) e conheça a CLI `mcp-publisher`. **Não publique**: a Central de Pedidos é um exercício em localhost, e o registry indexa, não hospeda.
3. **Derrube e reinicie o server, observando chamadas autossuficientes.** Suba, faça uma chamada, derrube o server, suba de novo e chame outra vez: cada chamada carrega tudo o que precisa nos argumentos, o comportamento stateless que a virada do dia 28 vai generalizar.

> O fluxo completo **401 → token → 200** (obter um token do Keycloak provisionado e passá-lo como Bearer) fica para a **gravação**: exige o container de fato no ar e o ajuste fino do `token.audience` com o authorization server. O que está validado aqui é a configuração da política de path e o `resource-metadata`; o percurso com token real é o item em aberto (ver Notas de produção).

---

## Para experimentar

**Troque a Central de Pedidos pelo seu domínio.** A estrutura é a mesma para qualquer negócio: troque `Pedido`/`PedidoRepository` pelo **estoque da sua loja**, pela **sua coleção**, pelo **seu sistema de chamados**. Proteja o **seu** `/mcp` com o mesmo perfil OIDC, escreva o `server.json` do **seu** domínio e, quando ele for real e hospedado, publique-o no registry. O protocolo é aberto: qualquer agente compatível fala com o seu server, agora com a porta trancada.

---

## Notas de produção [REVALIDAR]

Continuação da checklist da aula 6 (itens 1–8, validados no `order-hub`); aqui os itens **9–12** do roteiro 07.md:

- **Item 9, OIDC + Keycloak Dev Services.** ✅ **PARCIALMENTE VALIDADO** (17/07/2026): a proteção via política de path padrão do Quarkus HTTP (`/mcp,/mcp/*` + `authenticated`) e o `quarkus.oidc.resource-metadata.enabled=true` (RFC 9728) estão validados; o perfil `seguro` liga o OIDC e os Keycloak Dev Services (default fica tudo desligado). **Em aberto:** exercitar o fluxo completo com token real; os Dev Services sobem o container, mas o percurso **401 → token → 200** e o ajuste do `token.audience` (RFC 8707, deixado comentado) ficam para a gravação.
- **Item 10, fluxo do registry.** Em aberto: a CLI `mcp-publisher` e a verificação de namespace estão em **preview** (desde set/2025); o fluxo exato pode ter mudado, conferir na semana da gravação.
- **Item 11, negociação stateless.** Em aberto: como a extensão `quarkus-mcp-server-http` negocia versão entre o modo com sessão e o stateless do RC.
- **Item 12, status do RC de 28/07/2026.** Em aberto: confirmar, na semana da gravação, se o RC já saiu e o que entrou; ajustar o tempo verbal ("vai sair" → "saiu") nas seções sobre a virada stateless.

> **Sobre os testes automatizados:** o `OrderHubMcpTest` roda no **perfil de teste**, que mantém o OIDC **desligado** (o default de teste NÃO ativa `%seguro`), então os 4 testes de JSON-RPC contra o `/mcp` passam **sem Keycloak**. O teste do fluxo seguro (`401 → token → 200`) exigiria o container de Keycloak no ar e, por isso, fica para a gravação; não é criado aqui.
