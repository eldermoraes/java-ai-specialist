package com.eldermoraes.workflow;

import com.eldermoraes.ai.CardioAgent;
import com.eldermoraes.ai.GiClinicaAgent;
import com.eldermoraes.ai.NeuroAgent;
import com.eldermoraes.ai.OrtopediaAgent;
import dev.langchain4j.agentic.declarative.SupervisorAgent;
import dev.langchain4j.agentic.declarative.SupervisorRequest;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.service.V;

public interface DiagnosticoSupervisor {

    @SupervisorAgent(
            outputKey = "supervisorOutput",
            subAgents = {
                    CardioAgent.class,
                    NeuroAgent.class,
                    OrtopediaAgent.class,
                    GiClinicaAgent.class
            },
            responseStrategy = SupervisorResponseStrategy.LAST,
            maxAgentsInvocations = 2)
    String diagnosticar(@V("sintomas") String sintomas);

    @SupervisorRequest
    static String request(@V("sintomas") String sintomas) {
        return "Avalie os sintomas a seguir e roteie para o especialista médico mais apropriado "
                + "(Cardiologia, Neurologia, Ortopedia ou Clínica/Gastroenterologia). "
                + "O especialista deve produzir um diagnóstico estruturado com hipótese, "
                + "condutas iniciais, nível de urgência (VERDE/AMARELO/VERMELHO), sinais de alarme e exames sugeridos. "
                + "Sintomas relatados pelo paciente:\n\n" + sintomas;
    }
}
