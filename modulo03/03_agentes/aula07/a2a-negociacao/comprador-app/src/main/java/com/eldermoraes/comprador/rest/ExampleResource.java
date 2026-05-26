package com.eldermoraes.comprador.rest;

import com.eldermoraes.comprador.ai.ExampleGenerator;
import com.eldermoraes.comprador.dto.EntradaCompra;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/example")
public class ExampleResource {

    @Inject
    ExampleGenerator generator;

    @GET
    @Path("/compra")
    @Produces(MediaType.APPLICATION_JSON)
    @RunOnVirtualThread
    public EntradaCompra compra() {
        return generator.entradaExemplo();
    }
}
