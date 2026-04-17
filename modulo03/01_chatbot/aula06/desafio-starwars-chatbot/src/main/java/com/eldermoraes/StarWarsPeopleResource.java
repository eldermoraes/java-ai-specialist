package com.eldermoraes;

import com.eldermoraes.rest.StarWarsAPIService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@Path("/api/people")
@ApplicationScoped
public class StarWarsPeopleResource {

    @Inject
    @RestClient
    StarWarsAPIService starWarsAPIService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String getPeople() {
        return starWarsAPIService.getPeople();
    }
}
