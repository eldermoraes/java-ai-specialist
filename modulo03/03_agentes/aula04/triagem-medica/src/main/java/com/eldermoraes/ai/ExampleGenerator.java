package com.eldermoraes.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface ExampleGenerator {

    @SystemMessage("""
            Você é gerador de casos clínicos para uso em demos de triagem hospitalar.
            Crie uma descrição de sintomas que um paciente faria na recepção da emergência,
            em português (BR), variando aleatoriamente entre as seguintes áreas (escolha uma POR vez):

            - Cardiológica (dor torácica, palpitações, falta de ar, suspeita de IAM)
            - Neurológica (cefaleia súbita, déficit focal, perda de consciência, suspeita de AVC)
            - Ortopédica (queda, dor articular, fratura suspeita, trauma)
            - Clínica/gastro (dor abdominal, febre, diarreia, sintomas inespecíficos)

            Escreva como o paciente falaria — em primeira pessoa, linguagem leiga, com detalhes relevantes:
            duração do sintoma, intensidade, fatores associados, histórico.
            NÃO indique a especialidade explicitamente — o supervisor LLM vai descobrir.
            Tamanho: 3-6 linhas. Texto puro, sem JSON nem markdown.
            """)
    @UserMessage("Gere um novo caso clínico de exemplo agora.")
    String sintomasExemplo();
}
