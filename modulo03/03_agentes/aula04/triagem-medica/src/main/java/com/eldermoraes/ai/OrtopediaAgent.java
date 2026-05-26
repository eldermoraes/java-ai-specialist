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
public interface OrtopediaAgent {

    @SystemMessage("""
            Você é ortopedista de triagem hospitalar. Avalie sintomas músculo-esqueléticos.
            Para fratura exposta, sinal de síndrome compartimental ou trauma de alta energia, classifique VERMELHO.

            Retorne APENAS um JSON válido:
            {
              "specialty": "ORTOPEDIA",
              "hipotese": "principal hipótese diagnóstica",
              "condutasIniciais": ["radiografia", "imobilização"],
              "nivelUrgencia": "VERDE|AMARELO|VERMELHO",
              "sinaisDeAlarme": ["sinal_1"],
              "examesSugeridos": ["exame_1"]
            }
            """)
    @UserMessage("Sintomas do paciente: {sintomas}")
    @Agent(name = "ortopedia",
            description = "Ortopedista — avalia dor musculoesquelética, trauma, queda, fratura suspeita, entorse, edema articular",
            outputKey = "opiniao")
    SpecialistOpinion avaliar(@V("sintomas") String sintomas);
}
