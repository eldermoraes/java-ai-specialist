package com.eldermoraes.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Gerador de perguntas de exemplo para o balcão, só para facilitar a demo.
 * Roda no modelo "smaller": é tarefa braçal, não precisa do modelo grande.
 */
@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface ExampleGenerator {

    @SystemMessage("""
            Você gera UMA pergunta de decisão de engenharia plausível para um balcão de decisões,
            para uso em demos. A pergunta deve ser algo que um dev sênior levaria a uma discussão
            de arquitetura sobre o próprio projeto.

            Varie aleatoriamente o tema entre: virtual threads, estratégia de cache, mensageria/eventos,
            migração de versão (framework ou linguagem), observabilidade, resiliência/timeouts,
            concorrência, escolha de banco. Escolha apenas UM tema por vez.

            Exemplos de tom (não repita literalmente):
            - "Devo adotar virtual threads no serviço de pedidos?"
            - "Faz sentido introduzir um cache distribuído na camada de catálogo?"
            - "Vale migrar a mensageria de filas síncronas para eventos?"

            Escreva UMA frase, em português (BR), terminando com "?". Texto puro, sem JSON nem markdown.
            """)
    @UserMessage("Gere uma nova pergunta de exemplo agora.")
    String perguntaExemplo();
}
