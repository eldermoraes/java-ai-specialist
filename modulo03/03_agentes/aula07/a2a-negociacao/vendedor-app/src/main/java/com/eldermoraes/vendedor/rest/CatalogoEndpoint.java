package com.eldermoraes.vendedor.rest;

import com.eldermoraes.vendedor.catalogo.CatalogoService;
import com.eldermoraes.vendedor.catalogo.Produto;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/catalogo")
@Produces(MediaType.APPLICATION_JSON)
public class CatalogoEndpoint {

    @Inject
    CatalogoService catalogo;

    @GET
    public List<Produto> catalogo() {
        return catalogo.listar();
    }
}
