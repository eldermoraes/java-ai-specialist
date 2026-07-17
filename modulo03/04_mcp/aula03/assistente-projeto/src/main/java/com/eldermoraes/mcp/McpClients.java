package com.eldermoraes.mcp;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Fábrica dos clientes MCP do assistente — o CORAÇÃO desta aula.
 *
 * <p>Aqui a fiação é 100% PROGRAMÁTICA (em código Java), de propósito. A configuração
 * declarativa por {@code application.properties} ({@code quarkus.langchain4j.mcp.*}) é
 * assunto da próxima aula. Fazendo à mão, fica visível cada peça do protocolo:
 * <ul>
 *   <li>o <b>transporte</b> ({@link StdioMcpTransport} / {@link StreamableHttpMcpTransport}) —
 *       como o cliente conversa com o servidor;</li>
 *   <li>o <b>cliente</b> ({@link DefaultMcpClient}) — quem fala o protocolo MCP (JSON-RPC);</li>
 * </ul>
 *
 * <p>A mensagem central da aula está no método {@link #abrir(String, McpTransport)}:
 * a construção do cliente é IDÊNTICA para os dois servidores. <b>Trocou o transporte,
 * o resto do código fica intacto.</b>
 */
@ApplicationScoped
public class McpClients {

    private static final Logger log = Logger.getLogger(McpClients.class);

    /** Diretório que o servidor MCP de filesystem enxerga. Aponte para um projeto SEU. */
    @ConfigProperty(name = "assistente.projeto.diretorio")
    String diretorioProjeto;

    @ConfigProperty(name = "assistente.mcp.remoto.habilitado", defaultValue = "true")
    boolean remotoHabilitado;

    /** Servidor MCP remoto (Streamable HTTP, sem auth). */
    @ConfigProperty(name = "assistente.mcp.remoto.url")
    String remotoUrl;

    private McpClient filesystem; // tools via STDIO           (processo local: npx)
    private McpClient remoto;     // tools via Streamable HTTP  (servidor na nuvem)

    @PostConstruct
    void iniciar() {
        // ─── BLOCO 1: transporte STDIO ────────────────────────────────────────────────
        // O Quarkus sobe o servidor de filesystem de referência como um PROCESSO FILHO
        // (via npx) e conversa com ele por stdin/stdout. Nenhuma dessas tools
        // (list_directory, read_file, ...) existe no nosso código — elas vêm do servidor.
        McpTransport transporteLocal = new StdioMcpTransport.Builder()
                .command(List.of(
                        "npx", "-y", "@modelcontextprotocol/server-filesystem", diretorioProjeto))
                .logEvents(true)
                .build();
        filesystem = abrir("filesystem", transporteLocal);
        log.infof("Servidor MCP de filesystem conectado (stdio) sobre '%s'", diretorioProjeto);

        // ─── BLOCO 2: MESMO cliente, transporte diferente ─────────────────────────────
        // Streamable HTTP aponta para um servidor MCP REMOTO (sem auth). Repare que só
        // a linha do transporte muda em relação ao bloco acima — a ideia da aula.
        if (remotoHabilitado) {
            try {
                McpTransport transporteRemoto = new StreamableHttpMcpTransport.Builder()
                        .url(remotoUrl)
                        .logRequests(true)
                        .logResponses(true)
                        .build();
                remoto = abrir("remoto", transporteRemoto);
                log.infof("Servidor MCP remoto conectado (Streamable HTTP) em %s", remotoUrl);
            } catch (Exception e) {
                // Servidor público fora do ar não pode derrubar o assistente: seguimos
                // só com as tools de filesystem. (No provider ainda há uma 2ª rede de
                // segurança: failIfOneServerFails(false).)
                log.warnf("Servidor MCP remoto indisponível (%s). Seguindo apenas com filesystem. Causa: %s",
                        remotoUrl, e.getMessage());
                remoto = null;
            }
        }
    }

    /**
     * Constrói um {@link McpClient} sobre um transporte qualquer. É EXATAMENTE o mesmo
     * código para stdio e para Streamable HTTP — essa é a mensagem da aula.
     */
    private McpClient abrir(String chave, McpTransport transporte) {
        return new DefaultMcpClient.Builder()
                .key(chave)
                .transport(transporte)
                .build();
    }

    /** Servidores MCP atualmente conectados, para o {@code McpToolProvider} expor ao agente. */
    public List<McpClient> ativos() {
        return remoto == null ? List.of(filesystem) : List.of(filesystem, remoto);
    }

    @PreDestroy
    void encerrar() {
        fechar(filesystem);
        fechar(remoto);
    }

    private void fechar(McpClient cliente) {
        if (cliente == null) {
            return;
        }
        try {
            cliente.close();
        } catch (Exception e) {
            log.debugf("Falha ao fechar McpClient: %s", e.getMessage());
        }
    }
}
