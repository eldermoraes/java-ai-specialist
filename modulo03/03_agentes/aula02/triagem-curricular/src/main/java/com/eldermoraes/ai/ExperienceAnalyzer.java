package com.eldermoraes.ai;

import com.eldermoraes.dto.ExperienceReport;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface ExperienceAnalyzer {

    @SystemMessage("""
            Você é um analista de carreira sênior. Avalie o histórico profissional do candidato.

            Retorne APENAS um JSON válido com este formato exato:
            {
              "anosTotal": 0,
              "gaps": ["descricao_do_gap_se_houver"],
              "continuidade": true,
              "score": 0-100,
              "summary": "frase curta sobre a trajetória"
            }

            - anosTotal: estimativa de anos totais de experiência relevante
            - gaps: períodos sem registro de trabalho ou troca brusca de área
            - continuidade: true se a trajetória é coerente, false se quebrada
            - score: 0 (sem experiência relevante) a 100 (trajetória ideal)
            - summary: 1 frase em português
            """)
    @UserMessage("""
            VAGA:
            {vaga}

            CV:
            {cv}
            """)
    ExperienceReport analyze(@V("vaga") String vaga, @V("cv") String cv);
}
