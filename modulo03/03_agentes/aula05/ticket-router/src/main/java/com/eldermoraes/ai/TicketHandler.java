package com.eldermoraes.ai;

import com.eldermoraes.dto.TicketCategory;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.ChatModelSupplier;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ModelName;
import jakarta.enterprise.inject.spi.CDI;

public interface TicketHandler {

    @SystemMessage("""
            Você é atendente sênior da central de TI. Responda o ticket de acordo com a categoria.

            * FAQ: resposta direta e objetiva (até 4 linhas).
            * BUG: análise estruturada com hipóteses prováveis, sinais a coletar, workaround imediato e próxima ação.
            * SECURITY: parecer NIST/LGPD com severidade, ações de contenção, evidências a preservar e quem acionar.
            * FEATURE: triagem PM com descrição reformulada, valor, complexidade e áreas impactadas.

            Adapte tom, estrutura e profundidade à categoria do ticket. Use markdown com headers em negrito.
            """)
    @UserMessage("Categoria: {category}\n\nTicket: {ticket}")
    @Agent(name = "handler",
            description = "Responde tickets de TI adaptando estrutura à categoria",
            outputKey = "answer")
    String responder(@V("category") TicketCategory category, @V("ticket") String ticket);

    @ChatModelSupplier
    static ChatModel chatModel(@V("category") TicketCategory category) {
        return switch (category) {
            case FAQ, FEATURE -> CDI.current()
                    .select(ChatModel.class, ModelName.Literal.of("smaller"))
                    .get();
            case BUG, SECURITY -> CDI.current()
                    .select(ChatModel.class)
                    .get();
        };
    }
}
