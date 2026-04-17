package com.eldermoraes;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

@Path("/professor")
public class ProfessorResource {

    @Inject
    ProfessorService professorService;

    @GET
    public String chat(@QueryParam("message") String message) {
        return professorService.chat(message);
    }
}
