package com.eldermoraes;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;

@Path("/memory")
public class WithMemoryResource {

    @Inject
    WithMemoryService withMemoryService;

    @GET
    @Path("/{sessionId}")
    public String chat(@PathParam("sessionId") String sessionId, @QueryParam("message") String message) {
        return withMemoryService.chat(sessionId, message);
    }
}
