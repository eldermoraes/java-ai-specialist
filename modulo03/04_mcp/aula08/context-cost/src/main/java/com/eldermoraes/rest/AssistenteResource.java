package com.eldermoraes.rest;

import com.eldermoraes.ai.AssistenteEquipado;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Interface REST para conversar com o assistente superequipado.
 *
 * <p>Mesmo shape do endpoint da aula 03: um POST que recebe uma pergunta e devolve a
 * resposta. Roda em virtual thread ({@code @RunOnVirtualThread}) porque a chamada encadeia
 * operações bloqueantes: o modelo raciocina, decide chamar tools MCP, os servidores
 * respondem, e o modelo volta a raciocinar, tudo de forma síncrona.
 *
 * <p>Para ver a conta de contexto, o interessante é o log do request ao modelo (com
 * {@code log-requests=true}), não o corpo da resposta. Veja os cenários A, B e C no README.
 */
@Path("/api/assistente")
public class AssistenteResource {

    @Inject
    AssistenteEquipado assistente;

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    @RunOnVirtualThread
    public String perguntar(String pergunta) {
        return assistente.perguntar(pergunta);
    }
}
