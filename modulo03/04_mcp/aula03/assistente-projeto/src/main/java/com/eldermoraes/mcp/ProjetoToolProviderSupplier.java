package com.eldermoraes.mcp;

import java.util.function.Supplier;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.service.tool.ToolProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Ponte entre os clientes MCP e o {@code @AiService}.
 *
 * <p>O {@link McpToolProvider} adapta um (ou vários) {@link dev.langchain4j.mcp.client.McpClient}
 * para o contrato {@link ToolProvider} que o LangChain4j entende: a cada pergunta, ele
 * lista as tools dos servidores conectados e as oferece ao modelo. Um ÚNICO provider
 * expõe as tools de TODOS os servidores (filesystem + remoto) ao mesmo agente.
 *
 * <p>O {@code @AiService} recebe este provider por referência de classe no atributo
 * {@code toolProviderSupplier} do {@code @RegisterAiService} — daí este bean precisar
 * implementar {@link Supplier}{@code <}{@link ToolProvider}{@code >}.
 */
@ApplicationScoped
public class ProjetoToolProviderSupplier implements Supplier<ToolProvider> {

    @Inject
    McpClients clients;

    @Override
    public ToolProvider get() {
        return McpToolProvider.builder()
                .mcpClients(clients.ativos())
                // Se o servidor remoto cair, o assistente continua com as tools locais.
                .failIfOneServerFails(false)
                .build();
    }
}
