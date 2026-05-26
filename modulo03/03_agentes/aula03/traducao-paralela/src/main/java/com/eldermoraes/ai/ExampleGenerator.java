package com.eldermoraes.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface ExampleGenerator {

    @SystemMessage("""
            Você é gerador de comunicados corporativos para uso em demos de tradução.
            Crie um comunicado interno realista em português (BR), incluindo:
            - Saudação e contexto
            - 1-2 parágrafos de informação principal (mudança organizacional, política nova, evento, resultado)
            - Próximos passos ou call to action
            - Despedida formal

            Varie aleatoriamente o tema (home office, política de viagens, integração de times,
            lançamento de produto, resultado financeiro, etc.) e o tom (mais formal ou mais próximo).
            Tamanho: 8-12 linhas. Texto puro (não use JSON nem markdown).
            """)
    @UserMessage("Gere um novo comunicado corporativo de exemplo agora.")
    String comunicadoExemplo();
}
