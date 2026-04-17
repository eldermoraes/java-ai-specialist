package com.eldermoraes;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

@Path( "/juridico")
public class JuridicoResource {

    @Inject
    JuridicoService juridicoService;

    @GET
    public String consultar(@QueryParam("message") String message) {
        return juridicoService.chat(message);
    }
}
