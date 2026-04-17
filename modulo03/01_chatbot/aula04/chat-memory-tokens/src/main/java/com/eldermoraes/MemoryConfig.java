package com.eldermoraes;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class MemoryConfig {

    @ConfigProperty(name ="quarkus.langchain4j.openai.chat-model.model-name")
    String modelName;

    @Produces
    public ChatMemoryProvider chatMemoryProvider() {
        TokenCountEstimator tokenCountEstimator = new OpenAiTokenCountEstimator(modelName);

        return memoryId -> TokenWindowChatMemory.builder()
                .id(memoryId)
                .maxTokens(1000, tokenCountEstimator)
                .build();
    }
}
