package com.eldermoraes.rest;

import com.eldermoraes.ai.AssistenteProjeto;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Interface REST para conversar com o assistente.
 *
 * <p>Roda em virtual thread ({@code @RunOnVirtualThread}) porque a chamada encadeia
 * operações bloqueantes: o modelo raciocina, decide chamar uma tool MCP, o servidor MCP
 * responde, e o modelo volta a raciocinar, tudo de forma síncrona.
 */
@Path("/api/assistente")
public class AssistenteResource {

    @Inject
    AssistenteProjeto assistente;

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @RunOnVirtualThread
    public String perguntar(String pergunta) {
        return assistente.perguntar(pergunta);
    }
}
