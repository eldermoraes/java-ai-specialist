package com.eldermoraes.dto;

public record WarRoomEvent(String type, String agente, String outputKey, Object payload, String error) {

    public static WarRoomEvent aberto() {
        return new WarRoomEvent("ABERTO", null, null, null, null);
    }

    public static WarRoomEvent contribuicao(String agente, String outputKey, String conteudo) {
        return new WarRoomEvent("CONTRIBUICAO", agente, outputKey, conteudo, null);
    }

    public static WarRoomEvent relatorio(QuadroFinal quadro) {
        return new WarRoomEvent("RELATORIO", null, null, quadro, null);
    }

    public static WarRoomEvent error(String message) {
        return new WarRoomEvent("ERROR", null, null, null, message);
    }
}
