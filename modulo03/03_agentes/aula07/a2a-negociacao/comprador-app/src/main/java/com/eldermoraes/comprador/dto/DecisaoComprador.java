package com.eldermoraes.comprador.dto;

import java.math.BigDecimal;

public record DecisaoComprador(
        Acao acao,
        BigDecimal precoSugerido,
        String mensagem,
        String justificativa) {
}
