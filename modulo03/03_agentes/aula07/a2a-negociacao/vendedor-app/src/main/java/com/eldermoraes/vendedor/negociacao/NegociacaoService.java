package com.eldermoraes.vendedor.negociacao;

import com.eldermoraes.vendedor.ai.VendedorAgent;
import com.eldermoraes.vendedor.catalogo.CatalogoService;
import com.eldermoraes.vendedor.catalogo.Produto;
import com.eldermoraes.vendedor.dto.MensagemNegociacao;
import com.eldermoraes.vendedor.dto.RespostaVendedor;
import com.eldermoraes.vendedor.ws.VendedorDashboard;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.math.BigDecimal;

@ApplicationScoped
public class NegociacaoService {

    private static final Logger LOG = Logger.getLogger(NegociacaoService.class);

    @Inject
    VendedorAgent agent;

    @Inject
    CatalogoService catalogo;

    @Inject
    VendedorDashboard dashboard;

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
}
