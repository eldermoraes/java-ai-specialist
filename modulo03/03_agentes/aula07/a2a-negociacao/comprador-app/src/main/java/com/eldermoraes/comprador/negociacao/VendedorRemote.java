package com.eldermoraes.comprador.negociacao;

import com.eldermoraes.comprador.dto.MensagemNegociacao;
import com.eldermoraes.comprador.dto.RespostaVendedor;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "vendedor")
@Path("/a2a/negociacao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface VendedorRemote {

    @POST
    RespostaVendedor negociar(MensagemNegociacao mensagem);
}
