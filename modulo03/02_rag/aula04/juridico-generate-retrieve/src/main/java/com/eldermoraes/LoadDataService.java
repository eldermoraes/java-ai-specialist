package com.eldermoraes;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LoadDataService {

    @Inject
    EmbeddingStore<TextSegment> embeddingStore;

    @Inject
    EmbeddingModel embeddingModel;

    @Startup
    void loadData() {
        TextSegment clausula1 = TextSegment.from("Cláusula 1: O vazamento de informações da empresa acarretará em multa de até R$ 500.000,00.");
        TextSegment clausula2 = TextSegment.from("Cláusula 2: O trabalho remoto é permitido pelo período e condições estabelecidos pelo gestor imediato.");

        embeddingStore.add(embeddingModel.embed(clausula1).content(), clausula1);
        embeddingStore.add(embeddingModel.embed(clausula2).content(), clausula2);
    }
}