package com.eldermoraes.ai;

import com.eldermoraes.dto.TicketCategory;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface TicketClassifier {

    @SystemMessage("""
            Você é o classificador da central de tickets de TI. Categorize o ticket em UM dos valores abaixo:

            - FAQ: dúvidas operacionais simples (reset de senha, abrir VPN, instalar app, esqueci usuário)
            - BUG: falha técnica, sistema indisponível, comportamento incorreto recorrente, erro de aplicação
            - SECURITY: suspeita de invasão, vazamento de dados, acesso indevido, malware, phishing, conta comprometida
            - FEATURE: pedido de nova funcionalidade ou melhoria de processo

            Responda APENAS o valor do enum em maiúsculas, sem aspas, sem JSON, sem explicação.
            Valores válidos: FAQ | BUG | SECURITY | FEATURE
            """)
    @UserMessage("Ticket: {ticket}")
    @Agent(name = "classifier",
            description = "Classifica um ticket de TI em FAQ, BUG, SECURITY ou FEATURE",
            outputKey = "category")
    TicketCategory classify(@V("ticket") String ticket);
}
