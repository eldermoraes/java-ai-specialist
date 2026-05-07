package com.eldermoraes;

import com.eldermoraes.ai.IntentClassifierService;
import com.eldermoraes.ai.LlmAsScoringModel;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@ApplicationScoped
public class RagConfig {

    @Produces
    @Singleton
    public EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Produces
    @Singleton
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Produces
    @ApplicationScoped
    @Named("rhBm25")
    public InMemoryBM25Retriever rhBm25() {
        return new InMemoryBM25Retriever(3);
    }

    @Produces
    @ApplicationScoped
    @Named("tiBm25")
    public InMemoryBM25Retriever tiBm25() {
        return new InMemoryBM25Retriever(3);
    }

    @Produces
    public RetrievalAugmentor retrievalAugmentor(
            EmbeddingStore<TextSegment> store,
            EmbeddingModel embeddingModel,
            @ModelName("smaller") ChatModel chatModel,
            IntentClassifierService classifier,
            @Named("rhBm25") InMemoryBM25Retriever rhBm25,
            @Named("tiBm25") InMemoryBM25Retriever tiBm25,
            LlmAsScoringModel scoringModel) {

        PromptTemplate rewritePrompt = PromptTemplate.from("""
                Você reformula a pergunta atual do usuário com base no histórico da conversa,
                para usar em busca de informação.

                REGRAS:
                1. PRONOMES anafóricos sem antecedente explícito ("delas", "isso", "esse",
                   "aquilo", "esse problema") devem ser RESOLVIDOS usando o contexto anterior.
                   Exemplo: depois de discutir férias, "vender um terço delas"
                   → "Como vender um terço das férias?"

                2. PERGUNTAS curtas que introduzem um TÓPICO ou DOMÍNIO NOVO ("e de TI?",
                   "e benefícios?", "e o salário?") devem ser tratadas como pergunta
                   AUTÔNOMA sobre esse novo tópico — NÃO arraste o domínio anterior.
                   Exemplo: depois de "políticas de RH", "e de TI?"
                   → "Quais são as políticas e informações de TI?"
                   (NÃO "Políticas de RH que se aplicam à TI")

                3. Pergunta já autocontida: apenas formate para busca, mantendo o sentido.

                Histórico da conversa:
                {{chatMemory}}

                Pergunta atual: {{query}}

                Forneça APENAS a pergunta reformulada — sem prefixos, sem explicações, sem aspas.
                """);
        QueryTransformer queryTransformer = new CompressingQueryTransformer(chatModel, rewritePrompt);

        Filter rhFilter = metadataKey("departamento").isEqualTo("RH");
        Filter tiFilter = metadataKey("departamento").isEqualTo("TI");

        ContentRetriever rhRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .filter(rhFilter)
                .maxResults(3)
                .minScore(0.6)
                .build();

        ContentRetriever tiRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .filter(tiFilter)
                .maxResults(3)
                .minScore(0.6)
                .build();

        QueryRouter router = query -> {
            UserIntent intent = classifier.classify(query.text());
            System.out.println(">> Intenção detectada: " + intent);
            return switch (intent) {
                case RH -> List.of(rhRetriever, rhBm25);
                case TI -> List.of(tiRetriever, tiBm25);
                case AMBOS -> List.of(rhRetriever, rhBm25, tiRetriever, tiBm25);
            };
        };

        return DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .queryRouter(router)
                .contentAggregator(ReRankingContentAggregator.builder()
                        .scoringModel(scoringModel)
                        .maxResults(3)
                        .build())
                .contentInjector(DefaultContentInjector.builder()
                        .metadataKeysToInclude(List.of("source"))
                        .build())
                .build();
    }
}
