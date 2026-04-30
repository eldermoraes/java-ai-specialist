package com.eldermoraes;

import com.eldermoraes.ai.AssistantService;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/chat")
@Produces(MediaType.TEXT_PLAIN)
public class ChatResource {

    @Inject
    AssistantService assistant;

    @POST
    @Path("/{sessionId}")
    public String chat(@PathParam("sessionId") String sessionId, String message) {
        return assistant.message(sessionId, message);
    }
}
