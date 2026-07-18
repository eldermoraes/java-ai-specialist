package com.eldermoraes.ai;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Testes do client.
 *
 * <p>O teste de fumaça ({@link #assistenteFoiInjetado()}) prova o wiring: que o CDI conseguiu
 * construir o proxy do {@code @RegisterAiService} e que a augmentation do LangChain4j amarrou
 * o {@code @McpToolBox} ao client MCP declarativo, tudo isso sem chamar o modelo e sem o
 * server estar no ar. Por isso ele passa em qualquer ambiente (CI incluído), sem Ollama e sem
 * o vinyl-vault-server.
 */
@QuarkusTest
class AssistenteSeboTest {

    @Inject
    AssistenteSebo assistente;

    @Test
    void assistenteFoiInjetado() {
        assertNotNull(assistente,
                "O @RegisterAiService deveria ter sido construído pelo CDI (prova de wiring).");
    }

    /**
     * Teste de integração real: só roda com Ollama disponível e o vinyl-vault-server no ar
     * (porta 8080). Fica desabilitado por padrão para não quebrar o build de quem não tem o
     * ambiente. Para rodar: suba o server, garanta o Ollama, e remova o {@code @Disabled}.
     */
    @Test
    @Disabled("requer Ollama e o vinyl-vault-server no ar")
    void consultaDiscoRealViaMcp() {
        String resposta = assistente.responder("me mostra os dados do disco LP-001");
        assertNotNull(resposta);
        // LP-001 é o "Clube da Esquina" no acervo do server.
        assertTrue(resposta.toLowerCase().contains("clube da esquina"),
                "A resposta deveria refletir o disco real do acervo. Veio: " + resposta);
    }
}
