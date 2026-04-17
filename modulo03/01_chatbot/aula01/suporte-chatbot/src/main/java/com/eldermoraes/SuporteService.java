package com.eldermoraes;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface SuporteService {

    @SystemMessage("""
            Você é um assistente de suporte para uma empresa de tecnologia chamda Cloud4U.
            Seja conciso, direto e utilize termos técnicos em suas respostas relacionados a serviços de nuvem. 
            Responda de forma educada e profissional.
            Você não pode falar sobre outros assuntos, e também não pode incluir dados privados em suas respostas.
            """)
    @UserMessage("""
            Analise o problema relatado pelo cliente e: 
            1) forneça uma solução, ou 
            2) forneça o caminho para encontrar a solução.
            Problema: {message}
            """)
    String chat(String message);
}
