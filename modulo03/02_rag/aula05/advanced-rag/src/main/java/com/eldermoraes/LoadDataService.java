package com.eldermoraes;

import com.eldermoraes.ai.ContextService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@ApplicationScoped
public class LoadDataService {

    private static final String DOC_RH_NOME = "Manual de Benefícios da Acme Corp - 2025";
    private static final String DOC_RH = """
            Manual de Benefícios da Acme Corp - Edição 2025.

            Capítulo 1 - Férias: Os funcionários têm direito a 30 dias de descanso
            remunerado por ano, que podem ser fracionados em até três períodos.

            Capítulo 2 - Plano de Saúde: A empresa oferece plano de saúde nacional
            com cobertura para dependentes diretos sem custo adicional.

            Capítulo 3 - Vale-Refeição: O valor mensal é de R$ 1.200,00, creditado
            no quinto dia útil de cada mês.
            """;

    private static final String DOC_TI_NOME = "Política de Infraestrutura da Acme Corp - v3.2";
    private static final String DOC_TI = """
            Política de Infraestrutura da Acme Corp - Versão 3.2.

            Seção 1 - Senhas: A senha da VPN corporativa deve ter no mínimo 14
            caracteres, com letras maiúsculas, minúsculas, números e símbolos.

            Seção 2 - Acesso a Servidores: O acesso aos servidores de produção
            exige autenticação multifator (MFA) obrigatória.

            Seção 3 - Backup: Todos os dados críticos são replicados a cada 6
            horas para um datacenter secundário.
            """;

    @Inject
    @Named("rhStore")
    EmbeddingStore<TextSegment> rhStore;

    @Inject
    @Named("itStore")
    EmbeddingStore<TextSegment> itStore;

    @Inject
    @Named("rhBm25")
    InMemoryBM25Retriever rhBm25;

    @Inject
    @Named("itBm25")
    InMemoryBM25Retriever itBm25;

    @Inject
    EmbeddingModel embeddingModel;

    @Inject
    ContextService situator;

    @Startup
    void init() {
        ingest(DOC_RH, DOC_RH_NOME, rhStore, rhBm25);
        ingest(DOC_TI, DOC_TI_NOME, itStore, itBm25);
    }

    private void ingest(String documento, String nome, EmbeddingStore<TextSegment> store, InMemoryBM25Retriever bm25) {
        for (String chunk : documento.split("\\n\\s*\\n")) {
            chunk = chunk.strip();
            if (chunk.isEmpty()) continue;

            String context = situator.situate(documento, chunk);
            context = context == null ? "" : context.strip();
            String contextualized = context.isEmpty() ? chunk : context + "\n\n" + chunk;

            System.out.println(">> [" + nome + "] contexto gerado: " + (context.isEmpty() ? "(vazio — usando chunk cru)" : context));

            TextSegment segment = TextSegment.from(
                    contextualized,
                    Metadata.from("source", nome)
            );
            store.add(embeddingModel.embed(segment).content(), segment);
            bm25.add(segment);
        }
    }
}
