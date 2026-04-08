package com.eldermoraes;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path( "/sentimento")
public class SentimentoResource {

    @Inject
    SentimentoService sentimentoService;

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public Sentimento chat(@QueryParam("message") String message) {
        return sentimentoService.chat(message);
    }
}
