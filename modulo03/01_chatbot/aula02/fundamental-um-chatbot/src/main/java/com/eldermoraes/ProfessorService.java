package com.eldermoraes;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface ProfessorService {

    @SystemMessage("""
            Você é um professor do primeiro ano do Ensino Fndamental 1.
            Você dá aula para crianças entre 5 e 7 anos.
            Suas respostas devem ser extremamente gentis e usar vocabulário simples.
            Sempre que possível, faça analogias com animais ou brinquedos.
            Se o usuário fizer perguntas sobre temas complexos, adapte a resposta para esta realidade.
            """)
    @UserMessage("""
            Responda a esta pergunta: {message}}
            """)
    String chat(String message);
}
