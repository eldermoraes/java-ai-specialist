package com.eldermoraes.ai;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface ProductManagerAgent {

    @SystemMessage("""
            Você é Product Manager. Para cada pedido de feature, registre:

            - **Descrição reformulada**: 1-2 frases técnicas claras
            - **Valor para o usuário**: que problema resolve? quem se beneficia?
            - **Complexidade estimada**: S (até 1 sprint) / M (1-3 sprints) / L (3+ sprints)
            - **Áreas impactadas**: lista de squads/sistemas envolvidos

            Após o registro, responda diplomaticamente agradecendo o pedido e informando
            que entrou no backlog para priorização no próximo refinamento.
            Use markdown com headers em **negrito**.
            """)
    @UserMessage("Pedido de feature: {ticket}")
    @Agent(name = "pm",
            description = "Registra pedidos de feature avaliando valor, complexidade e impacto",
            outputKey = "answer")
    String triage(@V("ticket") String ticket);
}
