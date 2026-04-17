package com.eldermoraes;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.SessionScoped;

@RegisterAiService
@SessionScoped
public interface StreamingService {

    @SystemMessage("""
            Explique detalhadamente qualquer pergunta que o usuário fizer.
            Você deve ser prolixo e detalhado.
            Não se preocupe em dar informações em excesso. Seu papel é esse mesmo.
            """)
    @UserMessage("Responda em detalhes: {message}")
    Multi<String> chat(String message);
}
