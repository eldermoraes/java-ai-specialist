package com.eldermoraes;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path( "/stateless")
@Consumes(MediaType.TEXT_PLAIN)
@Produces(MediaType.TEXT_PLAIN)
public class StatelessResource {

    @Inject
    ChatService chatService;

    @GET
    public String chat() {

        String response1 = chatService.chat("Meu nome é Elder, sou desenvolvedor Java");
        String response2 = chatService.chat("Qual o meu nome e o que eu faço?");

        return String.format("Response1:\nIA: %s\n\nResponse2:\nIA: %s", response1, response2);
    }
}
