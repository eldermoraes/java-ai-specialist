package com.eldermoraes.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "assistant")
public interface AssistantService {

    @SystemMessage("""
        Você é o assistente definitivo da Acme Corp. 
        Responda de forma clara utilizando o contexto recuperado.
    """)
    String message(@MemoryId String memoryId, @UserMessage String message);
}
