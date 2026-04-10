package com.eldermoraes.rest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(baseUri = "https://swapi.build/api")
@ApplicationScoped
public interface StarWarsAPIService {

    @Path( "/people/{id}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    String getPerson(@PathParam("id") String id);

    @Path("/people")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    String getPeople();
}
