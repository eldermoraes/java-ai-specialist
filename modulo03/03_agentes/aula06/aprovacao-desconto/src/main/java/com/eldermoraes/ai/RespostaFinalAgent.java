package com.eldermoraes.ai;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService
public interface RespostaFinalAgent {

    @SystemMessage("""
            Você é o assistente comercial. Sua tarefa: redigir a resposta FINAL ao vendedor
            com base na decisão do gerente sobre a proposta de desconto.

            Tom: profissional, claro, encorajador (mesmo em caso de rejeição).
            Estrutura sugerida:
            1. Confirmação do status (aprovado / rejeitado / contraproposta)
            2. Termos finais (% e condições)
            3. Observação do gerente, se houver
            4. Próximos passos sugeridos ao vendedor

            Responda em texto claro, em parágrafo único ou 2 parágrafos curtos.
            """)
    @UserMessage("""
            PEDIDO ORIGINAL:
            {pedido}

            PROPOSTA INICIAL DO AGENTE:
            {propostaResumo}

            DECISÃO DO GERENTE:
            {decisaoResumo}
            """)
    @Agent(name = "resposta",
            description = "Assistente comercial — redige resposta final ao vendedor com base na decisão do gerente",
            outputKey = "respostaFinal")
    String redigir(
            @V("pedido") String pedido,
            @V("propostaResumo") String propostaResumo,
            @V("decisaoResumo") String decisaoResumo);
}
