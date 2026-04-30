package com.eldermoraes;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class InMemoryBM25Retriever implements ContentRetriever {

    private static final double K1 = 1.2;
    private static final double B = 0.75;

    private final List<TextSegment> segments = new ArrayList<>();
    private final List<List<String>> tokenizedDocs = new ArrayList<>();
    private final List<Integer> docLengths = new ArrayList<>();
    private final Map<String, Integer> docFreq = new HashMap<>();
    private double avgDocLen = 0.0;
    private final int maxResults;

    public InMemoryBM25Retriever(int maxResults) {
        this.maxResults = maxResults;
    }

    public synchronized void add(TextSegment segment) {
        List<String> tokens = tokenize(segment.text());
        segments.add(segment);
        tokenizedDocs.add(tokens);
        docLengths.add(tokens.size());
        for (String t : new HashSet<>(tokens)) {
            docFreq.merge(t, 1, Integer::sum);
        }
        avgDocLen = docLengths.stream().mapToInt(Integer::intValue).average().orElse(0.0);
    }

    @Override
    public List<Content> retrieve(Query query) {
        if (segments.isEmpty()) {
            return List.of();
        }

        List<String> queryTokens = tokenize(query.text());
        int n = segments.size();

        record Scored(int idx, double score) {}
        List<Scored> scored = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            List<String> doc = tokenizedDocs.get(i);
            int docLen = docLengths.get(i);

            Map<String, Integer> tf = new HashMap<>();
            for (String t : doc) tf.merge(t, 1, Integer::sum);

            double score = 0.0;
            for (String qt : queryTokens) {
                int df = docFreq.getOrDefault(qt, 0);
                int f = tf.getOrDefault(qt, 0);
                if (df == 0 || f == 0) continue;

                double idf = Math.log(1.0 + (n - df + 0.5) / (df + 0.5));
                double normalizedTf = (f * (K1 + 1.0)) / (f + K1 * (1.0 - B + B * docLen / avgDocLen));
                score += idf * normalizedTf;
            }
            if (score > 0.0) scored.add(new Scored(i, score));
        }

        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        int limit = Math.min(maxResults, scored.size());

        List<Content> out = new ArrayList<>(limit);
        for (int k = 0; k < limit; k++) {
            Scored s = scored.get(k);
            TextSegment seg = segments.get(s.idx());
            String preview = seg.text().substring(0, Math.min(60, seg.text().length())).replace("\n", " ");
            System.out.printf(">> [BM25] score=%.3f trecho='%s...'%n", s.score(), preview);
            out.add(Content.from(seg));
        }
        return out;
    }

    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return List.of();
        String[] parts = text.toLowerCase().split("[^\\p{L}\\p{Nd}]+");
        List<String> tokens = new ArrayList<>(parts.length);
        for (String p : parts) {
            if (!p.isEmpty()) tokens.add(p);
        }
        return tokens;
    }
}
