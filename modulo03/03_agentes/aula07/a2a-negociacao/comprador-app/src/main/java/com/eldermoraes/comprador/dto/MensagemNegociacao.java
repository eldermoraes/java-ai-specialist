package com.eldermoraes.comprador.dto;

import java.math.BigDecimal;

public record MensagemNegociacao(
        String compradorId,
        int rodada,
        String produto,
        BigDecimal ultimoValorProposto,
        String mensagem) {
}
