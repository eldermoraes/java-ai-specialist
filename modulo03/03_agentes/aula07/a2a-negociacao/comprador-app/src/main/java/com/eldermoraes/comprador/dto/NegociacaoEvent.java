package com.eldermoraes.comprador.dto;

import java.math.BigDecimal;

public record NegociacaoEvent(String type, Object payload, String message) {
    public static NegociacaoEvent iniciada(EntradaCompra entrada) {
        return new NegociacaoEvent("INICIADA", entrada, "Negociação iniciada via A2A");
    }

    public static NegociacaoEvent rodadaConcluida(RodadaNegociacao rodada) {
        return new NegociacaoEvent("RODADA", rodada, null);
    }

    public static NegociacaoEvent acordo(BigDecimal precoFinal, RespostaVendedor proposta) {
        return new NegociacaoEvent("ACORDO", proposta,
                "Acordo fechado em R$ " + precoFinal);
    }

    public static NegociacaoEvent impasse(String motivo) {
        return new NegociacaoEvent("IMPASSE", null, motivo);
    }

    public static NegociacaoEvent error(String error) {
        return new NegociacaoEvent("ERROR", null, error);
    }
}
