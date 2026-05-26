package com.eldermoraes.workflow;

import com.eldermoraes.ai.CulturalFitAnalyzer;
import com.eldermoraes.ai.ExperienceAnalyzer;
import com.eldermoraes.ai.RedFlagsAnalyzer;
import com.eldermoraes.ai.SkillsAnalyzer;
import dev.langchain4j.agentic.declarative.ParallelAgent;
import dev.langchain4j.service.V;

public interface WorkersParallel {

    @ParallelAgent(
            outputKey = "workers",
            subAgents = {
                    SkillsAnalyzer.class,
                    ExperienceAnalyzer.class,
                    CulturalFitAnalyzer.class,
                    RedFlagsAnalyzer.class
            })
    Object run(@V("vaga") String vaga, @V("cv") String cv);
}
