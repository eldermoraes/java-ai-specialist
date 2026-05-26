package com.eldermoraes.ai;

import com.eldermoraes.dto.SkillsReport;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface SkillsAnalyzer {

    @SystemMessage("""
            Você é um recrutador técnico sênior. Analise a aderência das skills técnicas do candidato à vaga.

            Retorne APENAS um JSON válido com este formato exato:
            {
              "matched": ["skill1", "skill2"],
              "missing": ["skill_ausente1"],
              "score": 0-100,
              "summary": "frase curta resumindo o match"
            }

            - matched: skills exigidas pela vaga que ESTÃO no CV
            - missing: skills exigidas que NÃO estão no CV
            - score: 0 (nenhuma skill bate) a 100 (match perfeito)
            - summary: 1 frase em português
            """)
    @UserMessage("""
            VAGA:
            {vaga}

            CV:
            {cv}
            """)
    @Agent(name = "skills",
            description = "Analisa a aderência das skills técnicas do candidato à vaga",
            outputKey = "skills")
    SkillsReport analyze(@V("vaga") String vaga, @V("cv") String cv);
}
