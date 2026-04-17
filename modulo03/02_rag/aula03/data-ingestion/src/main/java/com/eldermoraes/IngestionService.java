package com.eldermoraes;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.data.segment.TextSegment;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

@ApplicationScoped
public class IngestionService {

    @Inject
    EmbeddingStore<TextSegment> vectorDatabase;

    @Inject
    EmbeddingModel embeddingModel;

    @Startup
    void onStart() throws Exception {
        URL pdfUrl = getClass().getClassLoader().getResource("doc/context-engineering.pdf");
        Path pdf = Paths.get(pdfUrl.toURI());
        process(pdf, "TECH");
    }

    private void process(Path path, String department) {
        Document document = FileSystemDocumentLoader.loadDocument(
                path,
                new ApachePdfBoxDocumentParser()
        );

        document.metadata()
                .put("department", department)
                .put("date", LocalDate.now().toString());

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(500, 50))
                .embeddingModel(embeddingModel)
                .embeddingStore(vectorDatabase)
                .build();

        ingestor.ingest(document);
    }
}
