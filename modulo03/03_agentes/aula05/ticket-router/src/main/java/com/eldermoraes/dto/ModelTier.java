package com.eldermoraes.dto;

public enum ModelTier {
    FAST("gpt-oss:20b-cloud"),
    ROBUST("gpt-oss:120b-cloud");

    private final String modelId;

    ModelTier(String modelId) {
        this.modelId = modelId;
    }

    public String modelId() {
        return modelId;
    }
}
