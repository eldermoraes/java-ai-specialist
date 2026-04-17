package com.eldermoraes;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
public interface WithMemoryService {

    @SystemMessage("""
            Você é um assistente virtual.
            Sempre considere o histórico da conversa ao dar suas respostas.
            """)
    String chat(@MemoryId String sessionId, @UserMessage String message);
}
