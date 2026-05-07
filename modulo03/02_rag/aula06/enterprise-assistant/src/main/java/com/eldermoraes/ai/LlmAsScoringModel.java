package com.eldermoraes.ai;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class LlmAsScoringModel implements ScoringModel {

    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");

    @Inject
    @ModelName("smaller")
    ChatModel scorer;

    @Override
    public Response<List<Double>> scoreAll(List<TextSegment> segments, String query) {
        List<Double> scores = new ArrayList<>(segments.size());
        for (TextSegment seg : segments) {
            scores.add(scoreOne(seg.text(), query));
        }
        return Response.from(scores);
    }

    @Override
    public Response<Double> score(String text, String query) {
        return Response.from(scoreOne(text, query));
    }

    private double scoreOne(String text, String query) {
        String prompt = """
                Você é um classificador de relevância. Em uma escala inteira de 0 a 10,
                quão relevante é o TRECHO abaixo para responder à PERGUNTA?
                Responda APENAS com o número (sem explicações, sem texto extra).

                PERGUNTA:
                %s

                TRECHO:
                %s
                """.formatted(query, text);

        String resp = scorer.chat(prompt);
        Matcher m = FIRST_NUMBER.matcher(resp == null ? "" : resp);
        double raw = m.find() ? Math.min(10.0, Double.parseDouble(m.group())) : 0.0;
        double normalized = raw / 10.0;

        String preview = text.substring(0, Math.min(60, text.length())).replace("\n", " ");
        System.out.printf(">> [Reranker] score=%.2f trecho='%s...'%n", normalized, preview);
        return normalized;
    }
}
