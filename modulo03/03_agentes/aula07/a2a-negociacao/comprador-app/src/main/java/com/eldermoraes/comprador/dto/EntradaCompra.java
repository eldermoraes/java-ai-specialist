package com.eldermoraes.comprador.dto;

import java.math.BigDecimal;

public record EntradaCompra(
        String produto,
        BigDecimal orcamentoMax,
        int prazoMaxDias,
        String criterios) {
}
