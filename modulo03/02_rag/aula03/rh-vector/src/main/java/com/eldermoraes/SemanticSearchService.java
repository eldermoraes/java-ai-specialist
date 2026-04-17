package com.eldermoraes;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.parser.sql.SqlFilterParser;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class SemanticSearchService {

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    EmbeddingStore<TextSegment> pgVectorStore;

    private final SqlFilterParser sqlParser = new SqlFilterParser();

    @Startup
    void load() {
        store("Funcionários CLT têm direito a 30 dias de férias.", "BR", "RH");
        store("Empregados nos EUA possuem 15 dias de PTO pagos.", "EUA", "RH");
        store("O limite de reembolso de alimentação é de 200 reais.", "BR", "FINANCEIRO");
    }

    private void store(String policy, String country, String department) {
        TextSegment chunk = TextSegment.from(policy, Metadata.metadata("country", country).put("department", department));
        pgVectorStore.add(embeddingModel.embed(chunk).content(), chunk);
    }

    public List<String> userQuery(String query, String country, String department) {

        StringBuilder sqlFilter = new StringBuilder();
        sqlFilter.append("country = '").append(country).append("' AND department = '").append(department).append("'");

        Filter filter = sqlParser.parse(sqlFilter.toString());
        dev.langchain4j.data.embedding.Embedding queryEmbedding = embeddingModel.embed(query).content();

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .filter(filter)
                .maxResults(2)  // Retorna os 2 vizinhos mais próximos
                .minScore(0.6)  // Limiar de similaridade de cosseno (de 0 a 1)
                .build();

        return pgVectorStore.search(request).matches().stream()
                .map(match -> match.embedded().text())
                .toList();
    }
}
