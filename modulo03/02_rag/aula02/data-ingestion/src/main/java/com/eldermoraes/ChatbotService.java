package com.eldermoraes;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface ChatbotService {

    @SystemMessage("""
        Você é um assistente corporativo rigoroso.
        Responda à pergunta do usuário baseando-se EXCLUSIVAMENTE nas informações recuperadas do contexto local.
        Se a informação não estiver no contexto, diga "Não possuo esta informação nos meus arquivos".
    """)
    String chat(@UserMessage String message);
}
