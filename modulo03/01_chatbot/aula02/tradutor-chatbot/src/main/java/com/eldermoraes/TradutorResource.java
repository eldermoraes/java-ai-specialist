package com.eldermoraes;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

@Path( "/tradutor")
public class TradutorResource {

    @Inject
    TradutorService tradutorService;

    @GET
    public String traduzir(@QueryParam("idiomaOrigem") String idiomaOrigem,
                           @QueryParam("idiomaDestino") String idiomaDestino,
                           @QueryParam("texto") String texto) {
        return tradutorService.chat(idiomaOrigem, idiomaDestino, texto);
    }
}
