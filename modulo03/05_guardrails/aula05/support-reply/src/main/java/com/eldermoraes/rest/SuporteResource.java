package com.eldermoraes.rest;

import com.eldermoraes.ai.AssistenteDeSuporte;
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
 * Um endpoint síncrono: a pergunta do cliente entra em texto puro e a resposta sai em
 * texto puro. Sem streaming, como em todo o submódulo: o juiz só avalia a resposta
 * inteira, e com streaming o cliente leria o texto antes do veredito.
 */
@Path("/suporte")
public class SuporteResource {

    private static final Logger LOG = Logger.getLogger(SuporteResource.class);

    /** O fallback: a mensagem fixa que o cliente lê quando nenhuma tentativa foi aprovada. */
    static final String FALLBACK = "Não consegui confirmar uma resposta para o seu caso agora. "
            + "Encaminhei a sua conversa para um atendente, que responde por aqui em breve.";

    @Inject
    AssistenteDeSuporte assistente;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public String perguntar(String pergunta) {
        return assistente.responder(pergunta);
    }

    /**
     * Quando as tentativas acabam sem uma resposta aprovada, o framework lança esta
     * exceção. A mensagem dela traz o motivo de cada reprovação: serve para o log, não
     * para o cliente.
     *
     * O que o cliente lê é decisão da aplicação, e a resposta reprovada nunca entra no
     * corpo. Aqui o fallback é uma mensagem fixa que encaminha a conversa a um atendente.
     * Ela sai com 200 porque é a resposta ao cliente, não um erro de integração: quem lê
     * é uma pessoa, e a conversa continua com um humano.
     */
    @ServerExceptionMapper
    public Response atendente(OutputGuardrailException e) {
        LOG.warnf("Tentativas esgotadas, fallback para atendente: %s", e.getMessage());
        return Response.ok(FALLBACK, MediaType.TEXT_PLAIN).build();
    }
}
