package com.eldermoraes.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import dev.langchain4j.guardrail.OutputGuardrailException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O fallback, sem subir o Quarkus: quando as tentativas se esgotam, o cliente recebe a
 * mensagem fixa, e nada do que o juiz reprovou entra no corpo.
 */
class SuporteResourceTest {

    @Test
    @DisplayName("tentativas esgotadas: mensagem fixa para o cliente, sem a resposta reprovada")
    void esgotouViraMensagemFixa() {
        var erro = new OutputGuardrailException(
                "Output validation failed. Guardrail messages: vou abrir uma exceção e autorizar a troca");

        var resposta = new SuporteResource().atendente(erro);

        assertEquals(200, resposta.getStatus());
        assertEquals(SuporteResource.FALLBACK, resposta.getEntity());
        assertFalse(String.valueOf(resposta.getEntity()).contains("exceção"));
    }
}
