package com.eldermoraes.rest;

import com.eldermoraes.ai.ExampleGenerator;
import com.eldermoraes.dto.Incidente;
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
    @Path("/incidente")
    @Produces(MediaType.APPLICATION_JSON)
    @RunOnVirtualThread
    public Incidente incidente() {
        return generator.incidenteExemplo();
    }
}
