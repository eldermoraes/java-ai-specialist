package com.eldermoraes.workflow;

import com.eldermoraes.ai.EngineerAgent;
import com.eldermoraes.ai.FaqBot;
import com.eldermoraes.ai.ProductManagerAgent;
import com.eldermoraes.ai.SecurityOfficer;
import com.eldermoraes.dto.TicketCategory;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.service.V;

public interface TicketHandler {

    @ConditionalAgent(
            outputKey = "answer",
            subAgents = {
                    FaqBot.class,
                    EngineerAgent.class,
                    SecurityOfficer.class,
                    ProductManagerAgent.class
            })
    String handle(@V("ticket") String ticket);

    @ActivationCondition(value = FaqBot.class, description = "categoria == FAQ")
    static boolean isFaq(@V("category") TicketCategory category) {
        return category == TicketCategory.FAQ;
    }

    @ActivationCondition(value = EngineerAgent.class, description = "categoria == BUG")
    static boolean isBug(@V("category") TicketCategory category) {
        return category == TicketCategory.BUG;
    }

    @ActivationCondition(value = SecurityOfficer.class, description = "categoria == SECURITY")
    static boolean isSecurity(@V("category") TicketCategory category) {
        return category == TicketCategory.SECURITY;
    }

    @ActivationCondition(value = ProductManagerAgent.class, description = "categoria == FEATURE")
    static boolean isFeature(@V("category") TicketCategory category) {
        return category == TicketCategory.FEATURE;
    }
}
