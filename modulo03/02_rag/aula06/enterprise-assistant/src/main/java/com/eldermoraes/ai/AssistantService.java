package com.eldermoraes.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.SessionScoped;

@RegisterAiService
@SessionScoped
public interface AssistantService {

    @SystemMessage("""
        Você é o Enterprise Assistant da Acme Corp. Responda usando APENAS o contexto recuperado.
        Cite a origem da informação quando útil (campo source).
        Se a pergunta não estiver coberta pelo contexto, diga claramente que a informação
        não consta nos documentos indexados.
    """)
    String chat(@UserMessage String message);
}
