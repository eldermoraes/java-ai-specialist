package com.eldermoraes.ai;

import com.eldermoraes.dto.CulturalFitReport;
import com.eldermoraes.dto.ExperienceReport;
import com.eldermoraes.dto.RedFlagsReport;
import com.eldermoraes.dto.SkillsReport;
import com.eldermoraes.dto.TriagemReport;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService
public interface ReportSynthesizer {

    @SystemMessage("""
            Você é gerente de RH sênior. Pondere os 4 relatórios parciais para gerar um parecer final.

            Pesos:
            - Skills técnicas: 40%
            - Experiência: 25%
            - Fit cultural: 20%
            - Red Flags (penalidade): 15% (subtrair severidade do score)

            Recomendações possíveis:
            - "AVANCAR" se scoreFinal >= 70 e severidade red flags < 50
            - "REVISAO_HUMANA" se scoreFinal entre 50 e 69, ou red flags severos
            - "NAO_AVANCAR" se scoreFinal < 50

            Retorne APENAS um JSON válido com este formato exato (sem o campo skills/experience/cultural/redFlags, eles serão preenchidos depois):
            {
              "scoreFinal": 0-100,
              "recomendacao": "AVANCAR|REVISAO_HUMANA|NAO_AVANCAR",
              "justificativa": "parágrafo de 2-3 frases explicando a decisão"
            }
            """)
    @UserMessage("""
            VAGA:
            {vaga}

            RELATÓRIO DE SKILLS:
            {skills}

            RELATÓRIO DE EXPERIÊNCIA:
            {experience}

            RELATÓRIO DE FIT CULTURAL:
            {cultural}

            RELATÓRIO DE RED FLAGS:
            {redFlags}
            """)
    @Agent(name = "synthesizer",
            description = "Sintetiza um parecer final ponderando skills, experiência, fit cultural e red flags",
            outputKey = "triagemFinal")
    TriagemReport synthesize(
            @V("vaga") String vaga,
            @V("skills") SkillsReport skills,
            @V("experience") ExperienceReport experience,
            @V("cultural") CulturalFitReport cultural,
            @V("redFlags") RedFlagsReport redFlags);
}
