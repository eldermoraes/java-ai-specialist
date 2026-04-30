package com.eldermoraes.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

// Desativa o RetrievalAugmentor global: este serviço é usado durante a
// ingestão para situar trechos no documento, então não deve fazer RAG
// sobre os próprios stores que ainda estão sendo populados.
@RegisterAiService(
        modelName = "smaller",
        retrievalAugmentor = RegisterAiService.NoRetrievalAugmentorSupplier.class
)
public interface ContextService {

    @SystemMessage("""
        Você situa trechos de documentos para melhorar a busca por similaridade.
        Sua tarefa é gerar um contexto curto e objetivo (no máximo 50 palavras)
        que posicione o trecho dentro do documento completo (qual seção, qual
        assunto, qual entidade mencionada).
        Responda APENAS com o contexto situacional, sem prefácios nem comentários.
    """)
    @UserMessage("""
        Documento completo:
        <documento>
        {documento}
        </documento>

        Trecho a situar:
        <trecho>
        {trecho}
        </trecho>
    """)
    String situate(String documento, String trecho);
}
