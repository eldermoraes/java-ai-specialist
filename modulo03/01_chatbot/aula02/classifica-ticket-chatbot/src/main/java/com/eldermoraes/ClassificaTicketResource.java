package com.eldermoraes;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

@Path("/classifica-ticket")
public class ClassificaTicketResource {

    @Inject
    ClassificaTicketService classificaTicketService;

    @GET
    public String classificaTicket(@QueryParam("message") String message) {
        return classificaTicketService.chat(message);
    }
}
