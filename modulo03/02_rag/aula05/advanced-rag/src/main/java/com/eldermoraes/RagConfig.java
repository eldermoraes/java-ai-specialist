package com.eldermoraes;

import com.eldermoraes.ai.IntentClassifierService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
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
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.List;

@ApplicationScoped
public class RagConfig {

    @Inject
    IntentClassifierService classifier;

    @ApplicationScoped
    @Named("rhStore")
    @Produces
    public EmbeddingStore<TextSegment> rhStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @ApplicationScoped
    @Named("itStore")
    @Produces
    public EmbeddingStore<TextSegment> itStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @ApplicationScoped
    @Named("rhBm25")
    @Produces
    public InMemoryBM25Retriever rhBm25() {
        return new InMemoryBM25Retriever(3);
    }

    @ApplicationScoped
    @Named("itBm25")
    @Produces
    public InMemoryBM25Retriever itBm25() {
        return new InMemoryBM25Retriever(3);
    }

    @Produces
    public RetrievalAugmentor advancedRetrievalAugmentor(
            @Named("rhStore") EmbeddingStore<TextSegment> rhStore,
            @Named("itStore") EmbeddingStore<TextSegment> itStore,
            @Named("rhBm25") InMemoryBM25Retriever rhBm25,
            @Named("itBm25") InMemoryBM25Retriever itBm25,
            EmbeddingModel embeddingModel,
            @ModelName("smaller") ChatModel chatModel,
            ScoringModel scoringModel) {

        QueryTransformer queryTransformer = new CompressingQueryTransformer(chatModel);

        ContentRetriever rhRetriever = EmbeddingStoreContentRetriever
                .builder()
                .embeddingStore(rhStore)
                .embeddingModel(embeddingModel)
                .build();

        ContentRetriever itRetriever = EmbeddingStoreContentRetriever
                .builder()
                .embeddingStore(itStore)
                .embeddingModel(embeddingModel)
                .build();

        QueryRouter semanticRouter = query -> {

            UserIntent userIntent = classifier.classify(query.text());
            System.out.println(">> Intenção detectada: " + userIntent);

            return switch (userIntent) {
                case RH -> List.of(rhRetriever, rhBm25);
                case TI -> List.of(itRetriever, itBm25);
                case DESCONHECIDO -> List.of();
            };
        };

        return DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .queryRouter(semanticRouter)
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
