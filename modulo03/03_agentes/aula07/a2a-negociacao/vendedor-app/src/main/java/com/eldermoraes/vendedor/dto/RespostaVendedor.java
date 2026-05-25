package com.eldermoraes.vendedor.dto;

import java.math.BigDecimal;

public record RespostaVendedor(
        String produto,
        BigDecimal precoOferecido,
        String prazoEntrega,
        String condicoes,
        String mensagem,
        boolean limiteAtingido) {
}
