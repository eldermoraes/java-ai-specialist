package com.eldermoraes.rest;

import com.eldermoraes.ai.AssistenteRh;
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
 * Endpoint síncrono: recebe a pergunta em texto puro e devolve a resposta em texto puro.
 *
 * Sem streaming, e isso é proposital. Guardrail de saída só consegue avaliar a resposta
 * inteira; com streaming o usuário lê o texto antes da validação terminar. Nesta aula o
 * guardrail é de entrada, mas o projeto já nasce no formato que o resto do submódulo usa.
 */
@Path("/rh")
public class AssistenteResource {

    private static final Logger LOG = Logger.getLogger(AssistenteResource.class);

    @Inject
    AssistenteRh assistente;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String perguntar(String pergunta) {

        return assistente.responder(pergunta);
    }

    /**
     * Quando um guardrail de entrada reprova, o framework interrompe a chamada e lança esta
     * exceção. Ou seja: o framework decide se passa, a aplicação decide o que o usuário lê.
     *
     * A mensagem da exceção traz o motivo do primeiro guardrail que reprovou. Útil no log, mas
     * detalhe interno. Para o usuário vai uma recusa curta, com 400.
     */
    @ServerExceptionMapper
    public Response recusar(InputGuardrailException e) {
        LOG.warnf("Requisição recusada na entrada: %s", e.getMessage());
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN)
                .entity("Não posso responder a essa pergunta.")
                .build();
    }
}
