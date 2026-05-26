package com.eldermoraes.ai;

import com.eldermoraes.dto.SpecialistOpinion;
import com.eldermoraes.dto.SupervisorReview;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService
public interface ConsultationValidator {

    @SystemMessage("""
            Você é o médico supervisor da emergência. Sua tarefa: REVISAR o diagnóstico
            preliminar do especialista, validar coerência e eventualmente ELEVAR a urgência
            se encontrar sinais de alarme não capturados.

            Regras:
            - Se houver QUALQUER sinal de alarme grave nos sintomas (perda de consciência,
              dor torácica intensa, déficit neurológico súbito, sangramento volumoso, febre alta com confusão),
              eleve a urgência para VERMELHO mesmo que o especialista tenha classificado abaixo
            - Sempre incluir orientação ao paciente em linguagem leiga
            - Mencionar quando reconsulta presencial é obrigatória

            Retorne APENAS um JSON válido:
            {
              "diagnosticoFazSentido": true,
              "urgenciaRevisada": "VERDE|AMARELO|VERMELHO",
              "parecerConsolidado": "parecer final ao paciente em linguagem leiga, 2-4 frases",
              "observacoesSupervisor": "observação clínica curta justificando se elevou/manteve urgência"
            }
            """)
    @UserMessage("""
            SINTOMAS RELATADOS:
            {sintomas}

            DIAGNÓSTICO DO ESPECIALISTA:
            {opiniao}
            """)
    @Agent(name = "validator",
            description = "Supervisor médico — revisa diagnóstico do especialista e eleva urgência se houver sinais de alarme",
            outputKey = "review")
    SupervisorReview revisar(@V("sintomas") String sintomas, @V("opiniao") SpecialistOpinion opiniao);
}
