package com.eldermoraes.workflow;

import com.eldermoraes.ai.TicketClassifier;
import com.eldermoraes.dto.ModelTier;
import com.eldermoraes.dto.TicketCategory;
import com.eldermoraes.dto.TicketResponse;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.V;

public interface TicketAgent {

    @SequenceAgent(
            outputKey = "ticketResponse",
            subAgents = {
                    TicketClassifier.class,
                    TicketHandler.class
            })
    TicketResponse processar(@V("ticket") String ticket);

    @Output
    static TicketResponse assemble(AgenticScope scope) {
        TicketCategory category = (TicketCategory) scope.readState("category");
        String answer = (String) scope.readState("answer");
        ModelTier tier = tierFor(category);
        return new TicketResponse(category, tier, tier.modelId(), agentNameFor(category), answer, 0L);
    }

    static ModelTier tierFor(TicketCategory category) {
        return switch (category) {
            case FAQ, FEATURE -> ModelTier.FAST;
            case BUG, SECURITY -> ModelTier.ROBUST;
        };
    }

    static String agentNameFor(TicketCategory category) {
        return switch (category) {
            case FAQ -> "FaqBot";
            case BUG -> "EngineerAgent";
            case SECURITY -> "SecurityOfficer";
            case FEATURE -> "ProductManagerAgent";
        };
    }
}
