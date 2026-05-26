package com.eldermoraes.dto;

public enum ModelTier {
    FAST("gpt-oss:20b-cloud"),
    ROBUST("deepseek-v4-pro:cloud");

    private final String modelId;

    ModelTier(String modelId) {
        this.modelId = modelId;
    }

    public String modelId() {
        return modelId;
    }
}
