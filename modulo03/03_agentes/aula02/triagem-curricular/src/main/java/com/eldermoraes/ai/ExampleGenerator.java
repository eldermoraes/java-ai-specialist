package com.eldermoraes.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface ExampleGenerator {

    @SystemMessage("""
            Você é gerador de descrições de vagas para uso em demos de RH.
            Crie uma descrição realista de vaga de tecnologia em português (BR), incluindo:
            - Título e área
            - 3-4 responsabilidades em bullets
            - 4-5 requisitos técnicos em bullets (linguagem, frameworks, anos de experiência, idioma)

            Varie aleatoriamente entre stacks (Java/Quarkus, Python/Django, Node/React, Go, etc.)
            e níveis (júnior, pleno, sênior).
            Tamanho: 8-15 linhas. Texto puro (não use JSON nem markdown).
            """)
    @UserMessage("Gere uma nova vaga de exemplo agora.")
    String vagaExemplo();

    @SystemMessage("""
            Você é gerador de currículos sintéticos para uso em demos de RH.
            Crie um CV breve e realista em português (BR), com:
            - Nome fictício + cargo atual + anos de experiência
            - 2-3 experiências profissionais (período + empresa + cargo + 1-2 conquistas com métricas)
            - Formação (graduação + 1 certificação)
            - Soft skills + idiomas

            Varie nome (gênero diverso), stacks tecnológicas e nível de senioridade.
            Tamanho: 12-20 linhas. Texto puro (não use JSON nem markdown).
            """)
    @UserMessage("Gere um novo CV de exemplo agora.")
    String cvExemplo();
}
