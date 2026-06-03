package com.eldermoraes.dto;

import com.eldermoraes.hitl.ApprovalProposal;
import com.eldermoraes.hitl.ApprovalStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PropostaView(
        Long id,
        String vendedorId,
        String descricaoPedido,
        BigDecimal percentualProposto,
        String condicoes,
        String justificativaAgente,
        ApprovalStatus status,
        BigDecimal percentualFinal,
        String observacaoGerente,
        Instant criadoEm,
        Instant decididoEm) {
    public static PropostaView from(ApprovalProposal p) {
        return new PropostaView(p.id, p.vendedorId, p.descricaoPedido, p.percentualProposto,
                p.condicoes, p.justificativaAgente, p.status, p.percentualFinal,
                p.observacaoGerente, p.criadoEm, p.decididoEm);
    }
}
