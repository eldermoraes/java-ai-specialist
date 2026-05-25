package com.eldermoraes.dto;

public record TriagemReport(
        int scoreFinal,
        String recomendacao,
        String justificativa,
        SkillsReport skills,
        ExperienceReport experience,
        CulturalFitReport cultural,
        RedFlagsReport redFlags) {
}
