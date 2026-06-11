package com.eldermoraes.comprador.negociacao;

import com.eldermoraes.comprador.a2a.VendedorA2AClient;
import com.eldermoraes.comprador.ai.CompradorAgent;
import com.eldermoraes.comprador.dto.Acao;
import com.eldermoraes.comprador.dto.DecisaoComprador;
import com.eldermoraes.comprador.dto.EntradaCompra;
import com.eldermoraes.comprador.dto.MensagemNegociacao;
import com.eldermoraes.comprador.dto.NegociacaoEvent;
import com.eldermoraes.comprador.dto.RespostaVendedor;
import com.eldermoraes.comprador.dto.RodadaNegociacao;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.util.UUID;

@ApplicationScoped
public class NegociacaoCoordinator {

    private static final Logger LOG = Logger.getLogger(NegociacaoCoordinator.class);

    @Inject
    CompradorAgent comprador;

    @Inject
    VendedorA2AClient vendedorRemoto;

    @ConfigProperty(name = "negociacao.max-rodadas", defaultValue = "5")
    int maxRodadas;

    public Multi<NegociacaoEvent> negociar(EntradaCompra entrada) {
        return Multi.createFrom().emitter(emitter ->
                Thread.startVirtualThread(() -> runNegociacao(entrada, emitter)));
    }

    private void runNegociacao(EntradaCompra entrada,
                               MultiEmitter<? super NegociacaoEvent> emitter) {
        try {
            emitter.emit(NegociacaoEvent.iniciada(entrada));
            String compradorId = "comp-" + UUID.randomUUID().toString().substring(0, 8);

            BigDecimal ultimoPropostoComprador = null;
            String mensagemAtual = String.format(
                    "Olá! Procuro %s. Orçamento de até R$ %s, prazo máximo %d dias. Critérios: %s.",
                    entrada.produto(), entrada.orcamentoMax(), entrada.prazoMaxDias(), entrada.criterios());

            for (int rodada = 1; rodada <= maxRodadas; rodada++) {
                MensagemNegociacao msg = new MensagemNegociacao(
                        compradorId, rodada, entrada.produto(), ultimoPropostoComprador, mensagemAtual);

                RespostaVendedor respostaVendedor;
                try {
                    respostaVendedor = vendedorRemoto.negociar(msg);
                } catch (Exception e) {
                    LOG.error("Falha na chamada A2A ao vendedor", e);
                    emitter.emit(NegociacaoEvent.error("Falha A2A: " + e.getMessage()));
                    emitter.complete();
                    return;
                }

                DecisaoComprador decisao;
                try {
                    decisao = comprador.avaliar(
                            entrada.produto(),
                            entrada.orcamentoMax(),
                            entrada.prazoMaxDias(),
                            entrada.criterios() == null ? "—" : entrada.criterios(),
                            rodada, maxRodadas,
                            respostaVendedor.precoOferecido(),
                            respostaVendedor.prazoEntrega() == null ? "—" : respostaVendedor.prazoEntrega(),
                            respostaVendedor.condicoes() == null ? "—" : respostaVendedor.condicoes(),
                            respostaVendedor.mensagem() == null ? "—" : respostaVendedor.mensagem(),
                            respostaVendedor.limiteAtingido());
                } catch (Exception e) {
                    LOG.error("Falha avaliação do comprador agent", e);
                    emitter.emit(NegociacaoEvent.error("Falha do agente: " + e.getMessage()));
                    emitter.complete();
                    return;
                }

                emitter.emit(NegociacaoEvent.rodadaConcluida(new RodadaNegociacao(rodada, respostaVendedor, decisao)));

                if (decisao.acao() == Acao.ACEITAR) {
                    emitter.emit(NegociacaoEvent.acordo(respostaVendedor.precoOferecido(), respostaVendedor));
                    emitter.complete();
                    return;
                }
                if (decisao.acao() == Acao.DESISTIR) {
                    emitter.emit(NegociacaoEvent.impasse(
                            "Comprador desistiu na rodada " + rodada + ": " + decisao.justificativa()));
                    emitter.complete();
                    return;
                }

                ultimoPropostoComprador = decisao.precoSugerido();
                mensagemAtual = decisao.mensagem();
            }

            emitter.emit(NegociacaoEvent.impasse(
                    "Limite de " + maxRodadas + " rodadas atingido sem acordo"));
            emitter.complete();
        } catch (Exception e) {
            emitter.fail(e);
        }
    }
}
