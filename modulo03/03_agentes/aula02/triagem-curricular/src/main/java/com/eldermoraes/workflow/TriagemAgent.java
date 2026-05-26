package com.eldermoraes.workflow;

import com.eldermoraes.ai.ReportSynthesizer;
import com.eldermoraes.dto.CulturalFitReport;
import com.eldermoraes.dto.ExperienceReport;
import com.eldermoraes.dto.RedFlagsReport;
import com.eldermoraes.dto.SkillsReport;
import com.eldermoraes.dto.TriagemReport;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.V;

public interface TriagemAgent {

    @SequenceAgent(
            outputKey = "triagemFinal",
            subAgents = {
                    WorkersParallel.class,
                    ReportSynthesizer.class
            })
    TriagemReport triagem(@V("vaga") String vaga, @V("cv") String cv);

    @Output
    static TriagemReport assemble(AgenticScope scope) {
        TriagemReport parcial = (TriagemReport) scope.readState("triagemFinal");
        return new TriagemReport(
                parcial.scoreFinal(),
                parcial.recomendacao(),
                parcial.justificativa(),
                (SkillsReport) scope.readState("skills"),
                (ExperienceReport) scope.readState("experience"),
                (CulturalFitReport) scope.readState("cultural"),
                (RedFlagsReport) scope.readState("redFlags"));
    }
}
