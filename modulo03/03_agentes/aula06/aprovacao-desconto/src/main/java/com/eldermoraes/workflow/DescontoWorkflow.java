package com.eldermoraes.workflow;

import com.eldermoraes.ai.ComercialAgent;
import com.eldermoraes.ai.RespostaFinalAgent;
import com.eldermoraes.dto.ApprovalDecision;
import com.eldermoraes.dto.PropostaDesconto;
import com.eldermoraes.dto.PropostaView;
import com.eldermoraes.dto.VendedorEvent;
import com.eldermoraes.hitl.ApprovalProposal;
import com.eldermoraes.hitl.ApprovalService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.concurrent.TimeoutException;

@ApplicationScoped
public class DescontoWorkflow {

    private static final Logger LOG = Logger.getLogger(DescontoWorkflow.class);

    @Inject
    ComercialAgent comercial;

    @Inject
    RespostaFinalAgent respostaFinal;

    @Inject
    ApprovalService approvalService;

    public Multi<VendedorEvent> processarPedido(String vendedorId, String descricao) {
        return Multi.createFrom().emitter(emitter ->
                Thread.startVirtualThread(() -> runWorkflow(vendedorId, descricao, emitter)));
    }

    private void runWorkflow(String vendedorId, String descricao,
                             MultiEmitter<? super VendedorEvent> emitter) {
        try {
            emitter.emit(VendedorEvent.recebido());

            PropostaDesconto proposta = comercial.propor(descricao);
            ApprovalProposal entity = approvalService.criarPendente(vendedorId, descricao, proposta);
            emitter.emit(VendedorEvent.propostaPreparada(PropostaView.from(entity)));

            LOG.infof("Proposta %d aguardando decisão do gerente…", entity.id);
            ApprovalDecision decision;
            try {
                decision = approvalService.aguardarDecisao(entity.id);
            } catch (TimeoutException ex) {
                emitter.emit(VendedorEvent.error("Tempo esgotado aguardando aprovação do gerente."));
                emitter.complete();
                return;
            }

            PropostaView updated = approvalService.buscarPorId(entity.id);
            String propostaResumo = String.format("desconto: %s%%; condições: %s; justificativa: %s",
                    entity.percentualProposto, entity.condicoes, entity.justificativaAgente);
            String decisaoResumo = formatarDecisao(decision);

            String resposta = respostaFinal.redigir(descricao, propostaResumo, decisaoResumo);
            emitter.emit(VendedorEvent.decidida(updated, resposta));
            emitter.complete();
        } catch (Exception e) {
            LOG.error("Falha no workflow de desconto", e);
            emitter.fail(e);
        }
    }

    private String formatarDecisao(ApprovalDecision d) {
        return switch (d.status()) {
            case APROVADA -> "APROVADA tal qual proposta inicial. Observação: "
                    + (d.observacao() == null ? "—" : d.observacao());
            case REJEITADA -> "REJEITADA. Observação: " + (d.observacao() == null ? "—" : d.observacao());
            case CONTRAPROPOSTA -> String.format(
                    "CONTRAPROPOSTA: gerente sugeriu %s%%. Observação: %s",
                    d.percentualFinal(), d.observacao() == null ? "—" : d.observacao());
            default -> "Status: " + d.status();
        };
    }
}
