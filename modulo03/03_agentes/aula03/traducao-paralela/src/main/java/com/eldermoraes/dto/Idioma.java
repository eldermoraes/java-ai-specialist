package com.eldermoraes.dto;

import java.util.List;

public record Idioma(String nome, String codigo, String paisAlvo, String flag) {
    public static List<Idioma> alvos() {
        return List.of(
                new Idioma("Inglês", "EN", "EUA", "🇺🇸"),
                new Idioma("Espanhol", "ES", "Espanha", "🇪🇸"),
                new Idioma("Francês", "FR", "França", "🇫🇷"),
                new Idioma("Alemão", "DE", "Alemanha", "🇩🇪"),
                new Idioma("Chinês", "ZH", "China", "🇨🇳"));
    }
}
