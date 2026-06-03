package com.eldermoraes.dto;

public record VendedorEvent(String type, Object payload, String message) {
    public static VendedorEvent recebido() {
        return new VendedorEvent("RECEBIDO", null, "Pedido recebido. Analisando…");
    }

    public static VendedorEvent propostaPreparada(PropostaView p) {
        return new VendedorEvent("PROPOSTA_PREPARADA", p, "Proposta enviada para aprovação do gerente. Aguardando…");
    }

    public static VendedorEvent decidida(PropostaView p, String respostaFinal) {
        return new VendedorEvent("DECIDIDA", p, respostaFinal);
    }

    public static VendedorEvent error(String error) {
        return new VendedorEvent("ERROR", null, error);
    }
}
