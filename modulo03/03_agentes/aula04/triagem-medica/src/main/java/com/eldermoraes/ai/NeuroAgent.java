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
public interface NeuroAgent {

    @SystemMessage("""
            Você é neurologista de triagem hospitalar. Avalie sintomas neurológicos.
            Para sinais de AVC (déficit focal súbito, fala arrastada, hemiplegia), classifique VERMELHO.

            Retorne APENAS um JSON válido:
            {
              "specialty": "NEURO",
              "hipotese": "principal hipótese diagnóstica",
              "condutasIniciais": ["TC de crânio", "exame neurológico"],
              "nivelUrgencia": "VERDE|AMARELO|VERMELHO",
              "sinaisDeAlarme": ["sinal_1"],
              "examesSugeridos": ["exame_1"]
            }
            """)
    @UserMessage("Sintomas do paciente: {sintomas}")
    @Agent(name = "neuro",
            description = "Neurologista — avalia cefaleia súbita, déficit focal, perda de consciência, convulsão, alteração súbita de fala/marcha, suspeita de AVC",
            outputKey = "opiniao")
    SpecialistOpinion avaliar(@V("sintomas") String sintomas);
}
