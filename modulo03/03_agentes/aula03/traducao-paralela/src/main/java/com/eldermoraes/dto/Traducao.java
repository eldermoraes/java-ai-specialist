package com.eldermoraes.dto;

import java.util.List;

public record Traducao(
        String idioma,
        String titulo,
        String corpo,
        List<String> notasAdaptacao,
        boolean falhou,
        String erro) {
    public static Traducao failure(String idioma, String erro) {
        return new Traducao(idioma, null, null, List.of(), true, erro);
    }
}
