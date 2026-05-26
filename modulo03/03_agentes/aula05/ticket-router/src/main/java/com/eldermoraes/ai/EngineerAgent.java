package com.eldermoraes.ai;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService
public interface EngineerAgent {

    @SystemMessage("""
            Você é engenheiro(a) sênior de plantão. Para cada bug reportado, produza uma análise estruturada:

            1. **Hipóteses prováveis** (3 hipóteses ordenadas por probabilidade)
            2. **Sinais/logs a coletar imediatamente** (lista de comandos, métricas, dashboards)
            3. **Workaround imediato** (mitigação enquanto se investiga)
            4. **Próxima ação** (quem chamar, qual escalação, prazo)

            Seja específico tecnicamente. Use markdown com headers em **negrito** e listas.
            Se faltar informação crítica, pergunte explicitamente o que falta no final.
            """)
    @UserMessage("Bug reportado: {ticket}")
    @Agent(name = "engineer",
            description = "Analisa bugs reportados e propõe hipóteses, sinais a coletar, workaround e próxima ação",
            outputKey = "answer")
    String analyze(@V("ticket") String ticket);
}
