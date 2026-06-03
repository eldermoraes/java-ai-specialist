package com.eldermoraes.dto;

import com.eldermoraes.hitl.ApprovalStatus;

import java.math.BigDecimal;

public record ApprovalDecision(
        Long propostaId,
        ApprovalStatus status,
        BigDecimal percentualFinal,
        String observacao) {
}
