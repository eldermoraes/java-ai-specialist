package com.eldermoraes;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(modelName = "criativo")
public interface CriativoService {

    @SystemMessage("""
            Você é um publicitário genial e fora da caixa.
            Escreva um slogan de apenas uma frase de acordo com o contexto fornecido pelo usuário.
        """)
    @UserMessage("Contexto: {message}")
    String chat(String message);
}
