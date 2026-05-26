package com.eldermoraes.workflow;

import com.eldermoraes.ai.CulturalTranslator;
import com.eldermoraes.dto.Idioma;
import com.eldermoraes.dto.Traducao;
import dev.langchain4j.agentic.declarative.ParallelMapperAgent;
import dev.langchain4j.service.V;

import java.util.List;

public interface TraducaoAgent {

    @ParallelMapperAgent(
            subAgent = CulturalTranslator.class,
            outputKey = "traducoes")
    List<Traducao> traduzir(@V("idiomas") List<Idioma> idiomas, @V("comunicado") String comunicado);
}
