package com.eldermoraes.dto;

import java.util.List;

public record SkillsReport(
        List<String> matched,
        List<String> missing,
        int score,
        String summary) {
    public static SkillsReport empty(String reason) {
        return new SkillsReport(List.of(), List.of(), 0, "Falha ao analisar skills: " + reason);
    }
}
