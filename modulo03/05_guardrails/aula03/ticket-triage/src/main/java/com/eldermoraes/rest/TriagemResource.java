package com.eldermoraes.rest;

import com.eldermoraes.ai.TriagemDeChamados;
import dev.langchain4j.guardrail.OutputGuardrailException;
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
 * Endpoint síncrono: recebe o chamado em texto puro e devolve a triagem em texto puro.
 *
 * Sem streaming, e isso é proposital. Guardrail de saída só avalia a resposta inteira, e
 * com streaming o usuário lê o texto antes de a validação terminar.
 */
@Path("/triagem")
public class TriagemResource {

    private static final Logger LOG = Logger.getLogger(TriagemResource.class);

    @Inject
    TriagemDeChamados triagem;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String triar(String chamado) {

        return triagem.triar(chamado);
    }

    /** O mesmo chamado, pelo método cuja system message não pede o formato. */
    @POST
    @Path("/livre")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String triarSemFormato(String chamado) {

        return triagem.triarSemFormato(chamado);
    }

    /**
     * Quando as tentativas acabam sem uma resposta aprovada, o framework lança esta exceção.
     * A mensagem dela traz os motivos das reprovações: serve para o log, não para o usuário.
     *
     * O que o usuário lê é decisão da aplicação, e a resposta reprovada não entra no corpo.
     * Aqui o fallback é assumir a triagem manual e devolver 502, porque quem falhou foi o
     * serviço de trás, não o chamado que chegou.
     */
    @ServerExceptionMapper
    public Response triagemManual(OutputGuardrailException e) {
        LOG.warnf("Resposta reprovada na saída: %s", e.getMessage());
        return Response.status(Response.Status.BAD_GATEWAY)
                .type(MediaType.TEXT_PLAIN)
                .entity("Não consegui classificar o chamado agora. "
                        + "Ele foi registrado para triagem manual.")
                .build();
    }
}
