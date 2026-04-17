package com.eldermoraes;

import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface TradutorService {

    @UserMessage("""
            Traduza o seguinte texto do idioma {idiomaOrigem} para o idioma {idiomaDestino}.
            Mantenha o tom original e não adicione explicações extras.
            
            Texto: {texto}
            """)
    String chat(String idiomaOrigem, String idiomaDestino, String texto);
}
