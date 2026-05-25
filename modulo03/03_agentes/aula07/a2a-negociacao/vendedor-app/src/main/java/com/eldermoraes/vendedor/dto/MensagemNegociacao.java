package com.eldermoraes.vendedor.dto;

import java.math.BigDecimal;

public record MensagemNegociacao(
        String compradorId,
        int rodada,
        String produto,
        BigDecimal ultimoValorProposto,
        String mensagem) {
}
