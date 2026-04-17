package com.eldermoraes;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface JuridicoService {
    @SystemMessage("""
        Você é um advogado corporativo rigoroso. 
        Sua única fonte de verdade é a documentação fornecida pelo sistema (Contexto).
        Responda à pergunta do usuário baseando-se EXCLUSIVAMENTE nas cláusulas recuperadas.
        Se os documentos recuperados não abordarem o tema, diga "Esta informação não consta nos contratos indexados".
    """)
    String chat(@UserMessage String message);
}
