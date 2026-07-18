package com.eldermoraes.mcp;

import io.quarkiverse.langchain4j.mcp.auth.McpClientAuthProvider;
import io.quarkiverse.langchain4j.mcp.runtime.McpClientName;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Como o agente se apresenta a um servidor MCP protegido.
 *
 * <p>Na aula03, todos os servidores eram públicos e sem auth. Aqui aparece a peça que
 * faltava: um provider de autenticação. A cada requisição HTTP que o client "protegido"
 * faz ao servidor, a extensão chama {@link #getAuthorization} e usa o valor retornado
 * como header {@code Authorization}. Este provider devolve um Bearer token.
 *
 * <p><b>Regra de resolução</b> (por qual client cada provider responde):
 * <ul>
 *   <li>provider anotado com {@code @McpClientName("x")} atende só o client "x"
 *       (é o nosso caso: {@code "protegido"});</li>
 *   <li>provider sem {@code @McpClientName} atende todos os clients;</li>
 *   <li>nenhum provider para um client = aquele client fala sem auth.</li>
 * </ul>
 *
 * <p><b>Nota de segurança:</b> tokens não são propagados nos pings de health check
 * automáticos. Por isso, para servidores protegidos, vale usar
 * {@code auto-health-check=false} (o ping de fundo iria sem credencial). Aqui o client
 * "protegido" está {@code enabled=false}, então nada é enviado; a classe existe para
 * mostrar o par (client protegido + provider) e fechará o circuito quando construirmos
 * nosso próprio server MCP mais adiante no bloco.
 *
 * <p><b>Alternativas prontas (OIDC):</b> quando o token vier de um fluxo OIDC, a
 * comunidade já oferece providers plugáveis, sem escrever esta classe:
 * <ul>
 *   <li>{@code quarkus-langchain4j-oidc-mcp-auth-provider}: propaga o token do
 *       usuário logado;</li>
 *   <li>{@code quarkus-langchain4j-oidc-client-mcp-auth-provider}: usa uma identidade
 *       de serviço (client credentials).</li>
 * </ul>
 */
@ApplicationScoped
@McpClientName("protegido")
public class TokenAuthProvider implements McpClientAuthProvider {

    @ConfigProperty(name = "assistente.mcp.token")
    String token;

    @Override
    public String getAuthorization(Input input) {
        return "Bearer " + token;
    }
}
