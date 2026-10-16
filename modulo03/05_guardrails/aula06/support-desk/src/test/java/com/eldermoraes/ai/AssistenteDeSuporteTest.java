package com.eldermoraes.ai;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Teste de montagem: subir o Quarkus monta o container CDI, os três AI Services (o
 * assistente no modelo grande, o classificador e o juiz no modelo pequeno) e os oito
 * guardrails, com as listas e o domínio lidos de application.properties. Se a injeção
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

    @Inject
    JuizDePolitica juiz;

    @Test
    void servicosEstaoMontados() {
        assertNotNull(assistente);
        assertNotNull(classificador);
        assertNotNull(juiz);
    }
}
