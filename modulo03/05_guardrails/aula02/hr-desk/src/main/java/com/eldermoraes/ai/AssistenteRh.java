package com.eldermoraes.ai;

import com.eldermoraes.guardrails.DadoSensivelGuardrail;
import com.eldermoraes.guardrails.EscopoGuardrail;
import com.eldermoraes.guardrails.EscopoPorSentidoGuardrail;
import com.eldermoraes.guardrails.TamanhoGuardrail;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Assistente de RH.
 *
 * O que interessa nesta aula é a anotação abaixo. Ela declara os guardrails de entrada
 * que rodam ANTES desta chamada chegar ao modelo. A ordem da lista é a ordem de execução:
 * primeiro o mais barato (tamanho), por último o mais caro (chamada ao classificador).
 */
@RegisterAiService
public interface AssistenteRh {

    @SystemMessage("""
            Você é o assistente de RH da empresa. Responda apenas sobre temas de recursos
            humanos: férias, benefícios, folha de pagamento, ponto, admissão e desligamento.
            Seja direto e cordial. Se não souber, diga que não sabe.
            """)
    @InputGuardrails({
            TamanhoGuardrail.class,
            EscopoGuardrail.class,
            DadoSensivelGuardrail.class,
            EscopoPorSentidoGuardrail.class
    })
    String responder(String pergunta);
}
