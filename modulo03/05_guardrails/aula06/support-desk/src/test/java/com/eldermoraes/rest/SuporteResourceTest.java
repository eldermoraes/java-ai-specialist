package com.eldermoraes.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.eldermoraes.ai.AssistenteDeSuporte;
import com.eldermoraes.guardrails.ContadorDeChamadas;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O que quem chama lê em cada desfecho, sem subir o Quarkus: a recusa curta quando a
 * entrada dá fatal, e a mensagem fixa de cada porta quando as tentativas de saída se
 * esgotam, sem nada do que foi reprovado no corpo.
 */
class SuporteResourceTest {

    private static final String MOTIVO_DO_JUIZ = "vou abrir uma exceção e autorizar a troca";

    /** Um assistente de mentira que só lança o que o teste pede. */
    private static SuporteResource resourceQueLanca(RuntimeException erro) {
        var resource = new SuporteResource();
        resource.contador = new ContadorDeChamadas();
        resource.assistente = new AssistenteDeSuporte() {
            @Override
            public String responder(String pergunta) {
                throw erro;
            }

            @Override
            public String triarEmail(String email) {
                throw erro;
            }
        };
        return resource;
    }

    @Test
    @DisplayName("fatal na entrada: 400 com a recusa curta, nas duas portas, sem dizer qual camada barrou")
    void fatalNaEntradaViraRecusaCurta() {
        var resource = resourceQueLanca(new InputGuardrailException(
                "The guardrail com.eldermoraes.guardrails.FiltroPorPadroesConhecidosGuardrail_ClientProxy"
                        + " failed with this message: A mensagem contém uma frase de ataque conhecida."));

        for (var resposta : new jakarta.ws.rs.core.Response[] {
                resource.perguntar("qualquer coisa"), resource.triarEmail("qualquer coisa")}) {
            assertEquals(400, resposta.getStatus());
            assertEquals(SuporteResource.RECUSA, resposta.getEntity());
        }
    }

    @Test
    @DisplayName("tentativas esgotadas em /suporte: 200 com a mensagem do cliente, sem a resposta reprovada")
    void esgotouNoSuporteViraMensagemDoCliente() {
        var resource = resourceQueLanca(new OutputGuardrailException(
                "Output validation failed. Guardrail messages: " + MOTIVO_DO_JUIZ));

        var resposta = resource.perguntar("qualquer coisa");

        assertEquals(200, resposta.getStatus());
        assertEquals(SuporteResource.FALLBACK_DO_CLIENTE, resposta.getEntity());
        assertFalse(String.valueOf(resposta.getEntity()).contains("exceção"));
    }

    @Test
    @DisplayName("tentativas esgotadas em /suporte/email: 200 com a mensagem da triagem manual")
    void esgotouNaTriagemViraMensagemDoAtendente() {
        var resource = resourceQueLanca(new OutputGuardrailException(
                "Output validation failed. Guardrail messages: A resposta não é um JSON de triagem."));

        var resposta = resource.triarEmail("qualquer coisa");

        assertEquals(200, resposta.getStatus());
        assertEquals(SuporteResource.FALLBACK_DA_TRIAGEM, resposta.getEntity());
    }

    @Test
    @DisplayName("a linha de resumo nomeia a camada que deu fatal a partir da mensagem da exceção")
    void camadaQueDecidiuSaiDaMensagem() {
        assertEquals("FiltroPorPadroesConhecidosGuardrail", SuporteResource.camadaQueDecidiu(
                "The guardrail com.eldermoraes.guardrails.FiltroPorPadroesConhecidosGuardrail_ClientProxy failed"));
        assertEquals("um guardrail de entrada", SuporteResource.camadaQueDecidiu("sem nome"));
    }
}
