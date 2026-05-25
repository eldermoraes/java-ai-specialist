package com.eldermoraes.ai;

import com.eldermoraes.dto.SpecialistOpinion;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
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
              "hipotese": "principal hipótese diagnóstica",
              "condutasIniciais": ["ECG", "exame X"],
              "nivelUrgencia": "VERDE|AMARELO|VERMELHO",
              "sinaisDeAlarme": ["sinal_1"],
              "examesSugeridos": ["exame_1"]
            }
            """)
    @UserMessage("Sintomas do paciente: {it}")
    SpecialistOpinion avaliar(String sintomas);
}
