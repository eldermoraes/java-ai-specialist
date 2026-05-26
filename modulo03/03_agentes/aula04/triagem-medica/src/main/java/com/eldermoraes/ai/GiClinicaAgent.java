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
public interface GiClinicaAgent {

    @SystemMessage("""
            Você é clínico geral / gastroenterologista de triagem hospitalar.
            Avalie queixas gerais, digestivas e febris. Para sinais de abdome agudo,
            sepse ou desidratação severa, classifique VERMELHO.

            Retorne APENAS um JSON válido:
            {
              "specialty": "GI_CLINICA",
              "hipotese": "principal hipótese diagnóstica",
              "condutasIniciais": ["hidratação", "exames laboratoriais"],
              "nivelUrgencia": "VERDE|AMARELO|VERMELHO",
              "sinaisDeAlarme": ["sinal_1"],
              "examesSugeridos": ["exame_1"]
            }
            """)
    @UserMessage("Sintomas do paciente: {sintomas}")
    @Agent(name = "gi",
            description = "Clínico/gastroenterologista — avalia queixas digestivas, dor abdominal, febre, diarreia, vômitos, desidratação, sintomas inespecíficos",
            outputKey = "opiniao")
    SpecialistOpinion avaliar(@V("sintomas") String sintomas);
}
