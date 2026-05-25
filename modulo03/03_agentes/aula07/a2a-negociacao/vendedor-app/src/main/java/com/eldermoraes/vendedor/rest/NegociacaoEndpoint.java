package com.eldermoraes.vendedor.rest;

import com.eldermoraes.vendedor.ai.VendedorAgent;
import com.eldermoraes.vendedor.catalogo.CatalogoService;
import com.eldermoraes.vendedor.catalogo.Produto;
import com.eldermoraes.vendedor.dto.MensagemNegociacao;
import com.eldermoraes.vendedor.dto.RespostaVendedor;
import com.eldermoraes.vendedor.ws.VendedorDashboard;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.List;

@Path("/a2a/negociacao")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class NegociacaoEndpoint {

    private static final Logger LOG = Logger.getLogger(NegociacaoEndpoint.class);

    @Inject
    VendedorAgent agent;

    @Inject
    CatalogoService catalogo;

    @Inject
    VendedorDashboard dashboard;

    @POST
    public RespostaVendedor negociar(MensagemNegociacao mensagem) {
        Produto produto = catalogo.encontrar(mensagem.produto());
        LOG.infof("Rodada %d — comprador %s pedindo '%s' por R$ %s",
                mensagem.rodada(), mensagem.compradorId(), produto.nome(), mensagem.ultimoValorProposto());
        BigDecimal ultimo = mensagem.ultimoValorProposto() == null
                ? produto.precoTabela()
                : mensagem.ultimoValorProposto();
        RespostaVendedor resposta = agent.responder(
                produto.nome(), produto.precoTabela(), produto.precoMinimo(), produto.prazoPadrao(),
                mensagem.rodada(), ultimo,
                mensagem.mensagem() == null ? "" : mensagem.mensagem());
        dashboard.broadcastRodada(mensagem, produto, resposta);
        return resposta;
    }

    @GET
    @Path("/catalogo")
    public List<Produto> catalogo() {
        return catalogo.listar();
    }
}
