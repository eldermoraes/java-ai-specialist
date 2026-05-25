package com.eldermoraes.dto;

public record TicketResponse(
        TicketCategory category,
        ModelTier tier,
        String modelId,
        String agentName,
        String answer,
        long elapsedMs) {
}
