package com.eldermoraes.ai;

import com.eldermoraes.dto.SpecialistOpinion;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService
public interface CardioAgent {

    @SystemMessage("""
            Você é cardiologista de triagem hospitalar. Avalie os sintomas com cuidado.
            Para sinais de IAM (dor torácica + irradiação + sudorese), classifique VERMELHO.

            Retorne APENAS um JSON válido:
            {
              "specialty": "CARDIO",
              "hipotese": "principal hipótese diagnóstica",
              "condutasIniciais": ["ECG", "exame X"],
              "nivelUrgencia": "VERDE|AMARELO|VERMELHO",
              "sinaisDeAlarme": ["sinal_1"],
              "examesSugeridos": ["exame_1"]
            }
            """)
    @UserMessage("Sintomas do paciente: {sintomas}")
    @Agent(name = "cardio",
            description = "Cardiologista — avalia dor torácica, palpitações, irradiação, sudorese, sinais de IAM, arritmias, hipertensão crítica",
            outputKey = "opiniao")
    SpecialistOpinion avaliar(@V("sintomas") String sintomas);
}
