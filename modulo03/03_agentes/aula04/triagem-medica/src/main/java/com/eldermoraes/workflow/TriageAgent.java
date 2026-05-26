package com.eldermoraes.workflow;

import com.eldermoraes.ai.ConsultationValidator;
import com.eldermoraes.dto.SpecialistOpinion;
import com.eldermoraes.dto.SupervisorReview;
import com.eldermoraes.dto.TriageReport;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.V;

public interface TriageAgent {

    String DISCLAIMER = "Material didático: triagem inicial automatizada, "
            + "NÃO substitui avaliação médica presencial. Em caso de urgência, procure atendimento imediato.";

    @SequenceAgent(
            outputKey = "triageReport",
            subAgents = {
                    DiagnosticoSupervisor.class,
                    ConsultationValidator.class
            })
    TriageReport triar(@V("sintomas") String sintomas);

    @Output
    static TriageReport assemble(AgenticScope scope) {
        SpecialistOpinion opinion = (SpecialistOpinion) scope.readState("opiniao");
        SupervisorReview review = (SupervisorReview) scope.readState("review");
        return new TriageReport(
                opinion == null ? null : opinion.specialty(),
                opinion,
                review,
                DISCLAIMER);
    }
}
