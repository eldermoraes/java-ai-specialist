package com.eldermoraes.dto;

import java.util.List;

public record ExperienceReport(
        int anosTotal,
        List<String> gaps,
        boolean continuidade,
        int score,
        String summary) {
    public static ExperienceReport empty(String reason) {
        return new ExperienceReport(0, List.of(), false, 0, "Falha ao analisar experiência: " + reason);
    }
}
