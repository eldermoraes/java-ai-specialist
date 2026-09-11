package com.eldermoraes.ai;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Teste de fiação: subir o Quarkus monta o container CDI e o proxy do AI Service, com os
 * guardrails de saída já ligados na chamada. Se a injeção funciona, a montagem funcionou.
 *
 * Nenhum prompt é enviado, então este teste passa sem Ollama rodando.
 */
@QuarkusTest
class TriagemDeChamadosTest {

    @Inject
    TriagemDeChamados triagem;

    @Test
    void triagemEstaMontada() {
        assertNotNull(triagem);
    }
}
