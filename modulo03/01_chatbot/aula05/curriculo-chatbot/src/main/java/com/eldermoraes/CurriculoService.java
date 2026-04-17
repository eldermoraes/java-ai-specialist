package com.eldermoraes;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface CurriculoService {

    @UserMessage("""
        Analise o texto extraído de um currículo e extraia os dados do candidato.
        
        Currículo bruto: {message}
    """)
    Candidato chat(String message);
}
