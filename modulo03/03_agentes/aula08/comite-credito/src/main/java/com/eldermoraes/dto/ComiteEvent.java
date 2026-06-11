package com.eldermoraes.dto;

public record ComiteEvent(String type, Object payload, String error) {

    public static ComiteEvent recebido(String preview) {
        return new ComiteEvent("RECEBIDO", preview, null);
    }

    public static ComiteEvent votacao(ResultadoComite resultado) {
        return new ComiteEvent("VOTACAO", resultado, null);
    }

    public static ComiteEvent parecer(String texto) {
        return new ComiteEvent("PARECER", texto, null);
    }

    public static ComiteEvent error(String message) {
        return new ComiteEvent("ERROR", null, message);
    }
}
