package com.eldermoraes.ai;

import com.eldermoraes.Person;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
public interface StarWarsPersonService {

    @SystemMessage("""
            Você é um especialista em Star Wars. 
            Seu trabalho é pegar uma string em JSON, contendo dados de um personagem, e convertê-la no formato do objeto Person.
            
            O objeto Person tem um dado que o JSON não trará. É o Force.
            De acordo com o personagem, você deverá determinar para esse campo o seguinte:
            DARK_SIDE, LIGHT_SIDE, NEUTRAL
            
            Segue abaixo exemplos para te ajudar nessa tarefa:
                        
            name: Luke Skywalker, force: LIGHT_SIDE
            name: Darth Vader, force: DARK_SIDE
            name: Yoda, force: LIGHT_SIDE
            name: Darth Maul, force: DARK_SIDE
            name: Han Solo, force: NEUTRAL
            name: C-3PO, force: NEUTRAL
            """)
    @UserMessage("Analise o JSON e extraia os dados da Person: {message}")
    Person chat(String message);
}
