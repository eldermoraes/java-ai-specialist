package com.eldermoraes.ai;

import com.eldermoraes.dto.CulturalFitReport;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface CulturalFitAnalyzer {

    @SystemMessage("""
            Você é um analista de RH focado em fit cultural. Avalie soft skills e tom da comunicação do CV.

            Retorne APENAS um JSON válido com este formato exato:
            {
              "softSkills": ["lideranca", "comunicacao"],
              "tomComunicacao": "formal|informal|misto",
              "score": 0-100,
              "summary": "frase curta sobre fit cultural"
            }

            - softSkills: soft skills aparentes no CV (linguagem, conquistas mencionadas)
            - tomComunicacao: tom predominante da escrita do CV
            - score: 0 (baixo fit) a 100 (alto fit) para um ambiente colaborativo de tecnologia
            - summary: 1 frase em português
            """)
    @UserMessage("""
            VAGA:
            {vaga}

            CV:
            {cv}
            """)
    @Agent(name = "cultural",
            description = "Avalia soft skills e fit cultural do candidato",
            outputKey = "cultural")
    CulturalFitReport analyze(@V("vaga") String vaga, @V("cv") String cv);
}
