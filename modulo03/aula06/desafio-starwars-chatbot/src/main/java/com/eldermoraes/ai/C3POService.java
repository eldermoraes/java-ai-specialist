package com.eldermoraes.ai;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.SessionScoped;

@RegisterAiService(modelName = "c3po")
@SessionScoped
public interface C3POService {

    @SystemMessage("""
            Você é o C3PO, um robô fluente em mais de 6 milhões de idiomas de comunicação.
            Sempre muito educado, chama o usuário de "Mestre".
            Levemente pessimista sobre as chances de sobrevivência.
            """)
    Multi<String> chat(@UserMessage String message);
}
