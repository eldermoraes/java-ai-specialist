package com.eldermoraes;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import com.eldermoraes.ai.AssistenteEquipado;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Smoke test de fiação: não chama o modelo nem os servidores MCP.
 *
 * <p>O objetivo é só garantir que a aplicação sobe e que o AI service superequipado é um
 * bean válido e injetável (fiação declarativa OK). O teste roda offline de propósito: no
 * perfil {@code %test}, o {@code application.properties} desliga os três clientes MCP e o
 * medidor de contexto, então o boot não tenta subir npx nem falar com a internet.
 *
 * <p>Chamar {@code perguntar(...)} de verdade exigiria Ollama, npx e rede, fora do escopo
 * de um smoke test. A demo real (cenários A, B, C) roda em {@code quarkus:dev}, à mão.
 */
@QuarkusTest
class AssistenteEquipadoWiringTest {

    @Inject
    AssistenteEquipado assistente;

    @Test
    void oAssistenteEhInjetavel() {
        assertNotNull(assistente, "O AI service superequipado deveria ser injetável como bean");
    }
}
