package com.eldermoraes.ai;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Teste de montagem: subir o Quarkus monta o container CDI, os dois AI Services e os
 * guardrails, com a lista de padrões lida de application.properties. Se a injeção
 * funciona, a montagem funcionou.
 *
 * Nenhum prompt é enviado, então este teste passa sem Ollama rodando.
 */
@QuarkusTest
class AssistenteDeSuporteTest {

    @Inject
    AssistenteDeSuporte assistente;

    @Inject
    ClassificadorDeInjection classificador;

    @Test
    void servicosEstaoMontados() {
        assertNotNull(assistente);
        assertNotNull(classificador);
    }
}
