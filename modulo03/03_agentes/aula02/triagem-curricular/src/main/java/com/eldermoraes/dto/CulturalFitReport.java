package com.eldermoraes.dto;

import java.util.List;

public record CulturalFitReport(
        List<String> softSkills,
        String tomComunicacao,
        int score,
        String summary) {
    public static CulturalFitReport empty(String reason) {
        return new CulturalFitReport(List.of(), "indeterminado", 0, "Falha ao analisar fit cultural: " + reason);
    }
}
