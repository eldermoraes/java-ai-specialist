package com.eldermoraes.dto;

import java.math.BigDecimal;

public record PropostaDesconto(
        BigDecimal percentual,
        String condicoes,
        String justificativa) {
}
