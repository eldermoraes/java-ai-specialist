package com.eldermoraes.workflow;

import com.eldermoraes.ai.RelatorComite;
import com.eldermoraes.dto.Deliberacao;
import com.eldermoraes.dto.ResultadoComite;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.V;

public interface ComiteAgent {

    @SequenceAgent(outputKey = "deliberacao",
            subAgents = {
                    ComiteDeCredito.class,
                    RelatorComite.class
            })
    Deliberacao deliberar(@V("dossie") String dossie);

    @Output
    static Deliberacao assemble(AgenticScope scope) {
        return new Deliberacao(
                (ResultadoComite) scope.readState("resultadoComite"),
                (String) scope.readState("parecer"));
    }
}
