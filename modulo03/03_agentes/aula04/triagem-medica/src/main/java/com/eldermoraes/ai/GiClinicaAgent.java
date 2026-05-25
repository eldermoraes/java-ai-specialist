package com.eldermoraes.ai;

import com.eldermoraes.dto.SpecialistOpinion;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService
public interface GiClinicaAgent {

    @SystemMessage("""
            Você é clínico geral / gastroenterologista de triagem hospitalar.
            Avalie queixas gerais, digestivas e febris. Para sinais de abdome agudo,
            sepse ou desidratação severa, classifique VERMELHO.

            Retorne APENAS um JSON válido:
            {
              "hipotese": "principal hipótese diagnóstica",
              "condutasIniciais": ["hidratação", "exames laboratoriais"],
              "nivelUrgencia": "VERDE|AMARELO|VERMELHO",
              "sinaisDeAlarme": ["sinal_1"],
              "examesSugeridos": ["exame_1"]
            }
            """)
    @UserMessage("Sintomas do paciente: {it}")
    SpecialistOpinion avaliar(String sintomas);
}
