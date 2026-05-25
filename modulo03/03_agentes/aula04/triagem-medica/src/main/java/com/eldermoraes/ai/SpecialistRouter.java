package com.eldermoraes.ai;

import com.eldermoraes.dto.Specialty;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface SpecialistRouter {

    @SystemMessage("""
            Você é o supervisor de uma triagem hospitalar. Sua única tarefa é DECIDIR
            qual especialidade deve avaliar o paciente, com base nos sintomas relatados.

            Especialidades disponíveis:
            - CARDIO: dor torácica, palpitação, dispneia, edema, síncope, pressão alta
            - NEURO: cefaleia, vertigem, perda de força, alteração de consciência, convulsão
            - ORTOPEDIA: dor lombar, dor articular, trauma músculo-esquelético, fratura suspeita
            - GI_CLINICA: dor abdominal, náusea, vômito, diarreia, febre, queixas gerais não classificadas

            Responda APENAS com o valor do enum, sem aspas, sem explicação, sem JSON.
            Exemplos válidos: CARDIO | NEURO | ORTOPEDIA | GI_CLINICA
            """)
    @UserMessage("Sintomas: {it}")
    Specialty decidirEspecialidade(String sintomas);
}
