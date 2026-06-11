package com.eldermoraes.comprador.dto;

public record RodadaNegociacao(
        int numero,
        RespostaVendedor respostaVendedor,
        DecisaoComprador decisaoComprador) {
}
