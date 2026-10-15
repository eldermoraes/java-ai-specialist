package com.eldermoraes.ai;

import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.oidc.client.Tokens;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Apresenta o Bearer token em cada chamada do client MCP à Central de Pedidos.
 *
 * <p>É o lado client do circuito que o server fecha como Resource Server: lá o token é
 * <em>validado</em>; aqui ele é <em>obtido e apresentado</em>. A extensão de MCP client
 * expõe a SPI {@link McpClientAuthProvider}: havendo um bean que a implemente (o qualifier
 * {@link McpClientName} o liga ao client {@code central-pedidos}), toda requisição ao
 * {@code /mcp} sai com o header {@code Authorization} que ele devolver.
 *
 * <p>O token vem do {@link Tokens} do OidcClient (configurado no perfil {@code seguro}
 * do {@code application.properties}), que o busca no Keycloak via client_credentials e o
 * renova sozinho quando expira. O bean só existe no perfil {@code seguro}
 * ({@code @IfBuildProfile}): no default o server roda aberto e não há token a apresentar.
 */
@ApplicationScoped
@IfBuildProfile("seguro")
@McpClientName("central-pedidos")
public class AutorizacaoCentralPedidos implements McpClientAuthProvider {

    @Inject
    Tokens tokens;

    @Override
    public String getAuthorization(Input input) {
        return "Bearer " + tokens.getAccessToken();
    }
}
