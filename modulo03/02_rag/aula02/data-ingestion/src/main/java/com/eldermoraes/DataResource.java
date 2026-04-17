package com.eldermoraes;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

@Path("/data")
public class DataResource {

    @Inject
    ChatbotService chatbotService;

    @GET
    public String getChatbotResponse(@QueryParam("message") String message ) {
        return chatbotService.chat(message);
    }
}
