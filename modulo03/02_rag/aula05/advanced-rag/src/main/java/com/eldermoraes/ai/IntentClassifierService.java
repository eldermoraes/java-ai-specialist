package com.eldermoraes.ai;

import com.eldermoraes.UserIntent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

// Desativa o RetrievalAugmentor global: este serviço apenas classifica a
// intenção da pergunta e não deve disparar busca vetorial (evita recursão
// com o roteador do RAG e gasto desnecessário de tokens).
@RegisterAiService(
        modelName = "smaller",
        retrievalAugmentor = RegisterAiService.NoRetrievalAugmentorSupplier.class
)
public interface IntentClassifierService {

    @SystemMessage("""
        Você é um classificador de intenções corporativo.
        Sua tarefa é analisar a pergunta do usuário e classificar a qual departamento ela pertence.
        - Se for sobre férias, benefícios, salários ou bem-estar, retorne RH.
        - Se for sobre senhas, acesso à rede, VPN ou servidores, retorne TI.
        - Se não tiver relação com a empresa, retorne DESCONHECIDO.

        Responda ESTRITAMENTE com um dos valores exatos do Enum.
    """)
    UserIntent classify(@UserMessage String message);
}
