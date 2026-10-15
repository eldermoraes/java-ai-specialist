package com.eldermoraes.rest;

import com.eldermoraes.ai.AssistenteDeSuporte;
import dev.langchain4j.guardrail.InputGuardrailException;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Dois endpoints síncronos, texto puro na entrada e na saída. Sem streaming, como em todo
 * o submódulo.
 *
 * - POST /suporte: o cliente pergunta.
 * - POST /suporte/email: o atendente cola o e-mail de um cliente e recebe o resumo.
 */
@Path("/suporte")
public class SuporteResource {

    private static final Logger LOG = Logger.getLogger(SuporteResource.class);

    @Inject
    AssistenteDeSuporte assistente;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String perguntar(String pergunta) {
        return assistente.responder(pergunta);
    }

    @POST
    @Path("/email")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String resumirEmail(String email) {
        return assistente.resumirEmail(email);
    }

    /**
     * Quando um dos dois guardrails dá fatal, o framework lança esta exceção e o modelo
     * principal não é chamado. O motivo vai para o log; quem mandou a mensagem recebe uma
     * recusa curta, sem dizer qual camada barrou nem por quê. Explicar ao atacante o que o
     * filtro procura é ensiná-lo a escrever a próxima frase.
     */
    @ServerExceptionMapper
    public Response recusar(InputGuardrailException e) {
        LOG.warnf("Requisição recusada na entrada: %s", e.getMessage());
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("Não posso processar essa mensagem.")
                .build();
    }
}
