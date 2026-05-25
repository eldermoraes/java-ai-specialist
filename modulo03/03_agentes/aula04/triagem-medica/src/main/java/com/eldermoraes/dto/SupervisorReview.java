package com.eldermoraes.dto;

public record SupervisorReview(
        boolean diagnosticoFazSentido,
        UrgencyLevel urgenciaRevisada,
        String parecerConsolidado,
        String observacoesSupervisor) {
}
