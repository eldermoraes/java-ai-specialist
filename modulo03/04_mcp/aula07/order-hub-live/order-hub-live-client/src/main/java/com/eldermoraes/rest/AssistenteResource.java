package com.eldermoraes.rest;

import com.eldermoraes.ai.AssistentePedidos;

import dev.langchain4j.mcp.client.McpClient;
import io.quarkiverse.langchain4j.mcp.auth.McpAuthenticationException;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Interface REST para conversar com o assistente da Central de Pedidos.
 *
 * <p>Roda em virtual thread ({@code @RunOnVirtualThread}) porque a chamada encadeia
 * operações bloqueantes: o modelo raciocina, decide chamar uma tool MCP, o server responde,
 * e o modelo volta a raciocinar, tudo síncrono.
 *
 * <p><strong>A pré-checagem do MCP server.</strong> Antes de acionar o modelo, o endpoint
 * faz um {@code listTools()} direto no client MCP. O motivo: quando o server MCP falha
 * (fora do ar, ou protegido por OIDC e o client sem token), o tool provider do LangChain4j
 * degrada em silêncio: registra um WARN no log e segue adiante com a lista de tools
 * <em>vazia</em>. O modelo, que conhece os nomes das tools pelo system prompt, responde
 * "vou consultar, um momento" e encena a chamada como texto, sem nunca executá-la. Quem
 * olha só a tela conclui que o assistente travou. A pré-checagem transforma essa falha
 * silenciosa em uma resposta explícita no chat: no perfil {@code seguro} sem token, você
 * lê o 401 da proteção OIDC na própria conversa.
 */
@Path("/api/assistente")
public class AssistenteResource {

    @Inject
    AssistentePedidos assistente;

    @Inject
    @McpClientName("central-pedidos")
    McpClient centralPedidos;

    @ConfigProperty(name = "quarkus.langchain4j.mcp.central-pedidos.url")
    String urlDoServer;

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @RunOnVirtualThread
    public String perguntar(String pergunta) {
        try {
            centralPedidos.listTools();
        } catch (Exception e) {
            return explicarIndisponibilidade(e);
        }
        return assistente.perguntar(pergunta);
    }

    private String explicarIndisponibilidade(Exception erro) {
        for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
            if (causa instanceof McpAuthenticationException auth) {
                return """
                        Não consegui acessar a Central de Pedidos: o servidor MCP recusou a \
                        chamada com HTTP %d. É a proteção OIDC do perfil seguro em ação: sem \
                        um Bearer token válido, a chamada nem chega às tools. Para fechar o \
                        ciclo 401 -> token -> 200, suba este client também no perfil seguro \
                        (-Dquarkus.profile=seguro): ele passa a obter um token no Keycloak e \
                        a apresentá-lo em cada chamada.""".formatted(auth.getStatusCode());
            }
        }
        return """
                Não consegui acessar a Central de Pedidos: o servidor MCP não respondeu em \
                %s. Verifique se o order-hub-live-server está no ar.""".formatted(urlDoServer);
    }
}
