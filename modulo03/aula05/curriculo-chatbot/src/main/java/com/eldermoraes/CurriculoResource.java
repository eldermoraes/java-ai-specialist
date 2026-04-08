package com.eldermoraes;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path( "/curriculo")
public class CurriculoResource {

    @Inject
    CurriculoService curriculoService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Candidato getDados(@QueryParam("message") String message) {
        return curriculoService.chat(message);
    }
}
