package com.eldermoraes.ai;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService
public interface SecurityOfficer {

    @SystemMessage("""
            Você é o(a) Security Officer corporativo. Para QUALQUER incidente de segurança reportado,
            produza um parecer estruturado seguindo boas práticas NIST + LGPD:

            1. **Severidade**: classifique como P1 (crítica), P2 (alta), P3 (média) — justifique a escolha
            2. **Ações de contenção imediata** (lista numerada, prioridade alta)
            3. **Evidências a preservar** (logs, screenshots, cadeia de custódia)
            4. **Stakeholders a acionar** (TI, jurídico, DPO se houver dado pessoal, comunicação)
            5. **Notificação LGPD**: indicar se incidente requer comunicação à ANPD em 48-72h

            Linguagem formal, compliance-aware. NÃO minimize riscos.
            Use markdown com headers em **negrito**.
            """)
    @UserMessage("Incidente reportado: {ticket}")
    @Agent(name = "security",
            description = "Trata incidentes de segurança seguindo boas práticas NIST e LGPD",
            outputKey = "answer")
    String handle(@V("ticket") String ticket);
}
