package com.eldermoraes.vendedor.catalogo;

import java.math.BigDecimal;

public record Produto(
        String nome,
        BigDecimal precoTabela,
        BigDecimal precoMinimo,
        String prazoPadrao) {
}
