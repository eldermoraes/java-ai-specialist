package com.eldermoraes;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/rh")
public class RhResource {

    @Inject
    SemanticSearchService semanticSearchService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> rh(@QueryParam("query") String query,
                           @QueryParam("country") String country,
                           @QueryParam("department") String department) {
        return semanticSearchService.userQuery(query, country, department);
    }
}
