package com.eldermoraes;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path( "/suporte")
@Consumes(MediaType.TEXT_PLAIN)
@Produces(MediaType.TEXT_PLAIN)
public class SuporteResource {

    @Inject
    SuporteService suporteService;

    @GET
    public String chat(@QueryParam("message") String message) {
        return suporteService.chat(message);
    }
}
