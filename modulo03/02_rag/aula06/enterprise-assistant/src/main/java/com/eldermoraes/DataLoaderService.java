package com.eldermoraes;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

@ApplicationScoped
public class DataLoaderService {

    private static final String DOC_RH_NOME = "Manual de Benefícios da Acme Corp - 2026";
    private static final String DOC_RH_TEXTO = """
            Manual de Benefícios da Acme Corp - Edição 2026.

            Capítulo 1 - Férias.
            Os funcionários CLT da Acme Corp têm direito a 30 dias de descanso
            remunerado por ano completo de trabalho. As férias podem ser
            fracionadas em até três períodos, sendo que pelo menos um deles
            deve ter no mínimo 14 dias corridos.

            Capítulo 2 - Abono Pecuniário.
            O funcionário pode optar por converter até um terço (1/3) das
            suas férias em pagamento adicional, conhecido como abono
            pecuniário ou venda de férias. Esta solicitação deve ser feita
            ao gestor imediato com pelo menos 30 dias de antecedência ao
            início das férias.

            Capítulo 3 - Plano de Saúde.
            A Acme Corp oferece plano de saúde nacional com cobertura para
            o funcionário e seus dependentes diretos (cônjuge e filhos até
            21 anos, ou 24 se universitários). Sem coparticipação para
            consultas e exames básicos.

            Capítulo 4 - Vale-Refeição.
            O valor mensal do vale-refeição é de R$ 1.200,00 para todos os
            funcionários CLT, creditado no quinto dia útil de cada mês em
            cartão flexível aceito em restaurantes e supermercados.

            Capítulo 5 - Auxílio Home Office.
            Funcionários em regime de trabalho remoto recebem auxílio mensal
            de R$ 300,00 para cobrir despesas com internet e energia, pago
            junto com o salário.
            """;

    private static final String DOC_TI_NOME = "Manual de Infraestrutura da Acme Corp - v3.2";
    private static final Path PDF_PATH = Paths.get("target/doc/manual-rh.pdf");

    @Inject
    EmbeddingStore<TextSegment> store;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    @Named("rhBm25")
    InMemoryBM25Retriever rhBm25;

    @Inject
    @Named("tiBm25")
    InMemoryBM25Retriever tiBm25;

    @Inject
    PdfFactory pdfFactory;

    @Startup
    void init() throws Exception {
        pdfFactory.generate(DOC_RH_NOME, DOC_RH_TEXTO, PDF_PATH);
        Document docRh = FileSystemDocumentLoader.loadDocument(PDF_PATH, new ApachePdfBoxDocumentParser());

        URL tiUrl = getClass().getClassLoader().getResource("doc/manual-ti.md");
        if (tiUrl == null) {
            throw new IllegalStateException("manual-ti.md não encontrado no classpath em doc/manual-ti.md");
        }
        Document docTi = FileSystemDocumentLoader.loadDocument(Paths.get(tiUrl.toURI()), new TextDocumentParser());

        ingest(docRh, DOC_RH_NOME, "RH", rhBm25);
        ingest(docTi, DOC_TI_NOME, "TI", tiBm25);

        System.out.println(">> [DataLoader] Ingestão concluída.");
    }

    private void ingest(Document doc, String docName, String departamento, InMemoryBM25Retriever bm25) {
        for (TextSegment seg : DocumentSplitters.recursive(500, 50).split(doc)) {
            String contextual = "[Documento: " + docName + "]\n\n" + seg.text();
            TextSegment prefixed = TextSegment.from(
                    contextual,
                    Metadata.from("departamento", departamento).put("source", docName)
            );
            store.add(embeddingModel.embed(prefixed).content(), prefixed);
            bm25.add(prefixed);
        }
        System.out.println(">> [DataLoader] " + docName + " ingerido em " + departamento);
    }
}
