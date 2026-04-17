package com.eldermoraes;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/criatividade")
@Consumes(MediaType.TEXT_PLAIN)
@Produces(MediaType.TEXT_PLAIN)
public class CriatividadeResource {

    @Inject
    CriativoService criativoService;

    @Inject
    DeterministicoService deterministicoService;

    @GET
    public String chat(@QueryParam("message") String message) {
        String criativo = criativoService.chat(message);
        String deterministico = deterministicoService.chat(message);

        return """
               === DEMONSTRAÇÃO DE SAMPLING ===
               
               [Criativo (Temp: 0.9, Top-P: 0.9)]
               %s
               
               [Determinístico (Temp: 0.0, Top-K: 1)]
               %s
               
               """.formatted(criativo, deterministico);
    }
}
