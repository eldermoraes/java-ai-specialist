package com.eldermoraes;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface SentimentoService {

    @SystemMessage("""
               Você é um analisador de sentimentos.
               Responda APENAS com uma das seguintes palavras, sem nenhum texto adicional:
               POSITIVO, NEGATIVO, NEUTRO, DESCONHECIDO
            """)
    @UserMessage("Analise o sentimento da seguinte mensagem: {message}")
    Sentimento chat(String message);
}