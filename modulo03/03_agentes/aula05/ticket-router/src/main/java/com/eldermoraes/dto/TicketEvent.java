package com.eldermoraes.dto;

public record TicketEvent(String type, Object payload, String error) {
    public static TicketEvent received(String preview) {
        return new TicketEvent("RECEIVED", preview, null);
    }

    public static TicketEvent classification(TicketCategory category, ModelTier tier, String modelId, String agentName) {
        return new TicketEvent("CLASSIFICATION",
                new ClassificationPayload(category, tier, modelId, agentName), null);
    }

    public static TicketEvent answer(TicketResponse response) {
        return new TicketEvent("ANSWER", response, null);
    }

    public static TicketEvent error(String message) {
        return new TicketEvent("ERROR", null, message);
    }

    public record ClassificationPayload(TicketCategory category, ModelTier tier, String modelId, String agentName) {
    }
}
