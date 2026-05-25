package com.eldermoraes.dto;

import java.util.List;

public record SpecialistOpinion(
        String hipotese,
        List<String> condutasIniciais,
        UrgencyLevel nivelUrgencia,
        List<String> sinaisDeAlarme,
        List<String> examesSugeridos) {
}
