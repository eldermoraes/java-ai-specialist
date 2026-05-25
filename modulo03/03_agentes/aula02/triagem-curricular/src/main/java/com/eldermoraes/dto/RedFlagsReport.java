package com.eldermoraes.dto;

import java.util.List;

public record RedFlagsReport(
        List<String> inconsistencias,
        List<String> alertas,
        int severidade,
        String summary) {
    public static RedFlagsReport empty(String reason) {
        return new RedFlagsReport(List.of(), List.of(), 0, "Falha ao analisar red flags: " + reason);
    }
}
