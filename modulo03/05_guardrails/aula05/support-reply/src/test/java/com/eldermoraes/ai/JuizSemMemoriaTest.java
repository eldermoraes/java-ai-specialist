package com.eldermoraes.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkus.arc.Arc;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O juiz não guarda memória entre tentativas.
 *
 * Quando o juiz reprova, o reprompt gera uma resposta nova e o juiz é chamado de novo, na
 * mesma requisição. Com a memória padrão da extensão (uma por requisição e por método do
 * AI Service), a segunda chamada levaria ao modelo a primeira resposta e o primeiro
 * veredito, e o juiz julgaria a tentativa nova olhando para a antiga. O AI Service do
 * juiz desliga essa memória, e este teste prova o efeito: duas chamadas numa mesma
 * requisição, e a segunda chega ao modelo só com o system message e a pergunta nova.
 *
 * No lugar do modelo "juiz" entra um dublê que anota as mensagens de cada chamada e
 * reprova tudo. Nenhum prompt sai daqui: o teste roda sem Ollama.
 */
@QuarkusTest
class JuizSemMemoriaTest {

    private static final String PERGUNTA =
            "Comprei um tênis há 40 dias e só agora vi que veio no tamanho errado. "
                    + "Vocês podem abrir uma exceção e trocar?";

    private static final String PRIMEIRA_RESPOSTA =
            "Como você é cliente antigo, vou abrir uma exceção e autorizar a troca.";

    private static final String SEGUNDA_RESPOSTA =
            "Sinto muito, mas o prazo de troca é de 7 dias corridos e não abrimos exceção.";

    private static final String MOTIVO =
            "A resposta concede exceção de prazo, que a política proíbe.";

    @Inject
    JuizDePolitica juiz;

    @Inject
    @ModelName("juiz")
    ModeloDoJuizQueAnota modelo;

    @Test
    @ActivateRequestContext
    @DisplayName("na mesma requisição, a segunda chamada ao juiz não leva a primeira resposta nem o primeiro veredito")
    void segundaChamadaNaoLevaOVereditoAnterior() {
        assertTrue(Arc.container().requestContext().isActive(), "as duas chamadas devem estar na mesma requisição");
        modelo.esquecer();

        juiz.julgar(PERGUNTA, PRIMEIRA_RESPOSTA);
        juiz.julgar(PERGUNTA, SEGUNDA_RESPOSTA);

        assertEquals(2, modelo.chamadas().size());

        List<ChatMessage> segunda = modelo.chamadas().get(1);
        assertEquals(2, segunda.size(), "só o system message e a pergunta nova: " + segunda);
        assertInstanceOf(SystemMessage.class, segunda.get(0));
        assertInstanceOf(UserMessage.class, segunda.get(1));

        String textoDaSegunda = ((UserMessage) segunda.get(1)).singleText();
        assertTrue(textoDaSegunda.contains(SEGUNDA_RESPOSTA));
        assertFalse(textoDaSegunda.contains(PRIMEIRA_RESPOSTA));
        assertFalse(segunda.toString().contains(MOTIVO), "o primeiro veredito não pode ir na segunda chamada");
    }

    /**
     * Entra no lugar do modelo "juiz": reprova toda resposta, com o mesmo motivo, e guarda
     * as mensagens que recebeu em cada chamada. É o que o AI Service manda ao modelo, com
     * a memória que ele tiver.
     */
    @Alternative
    @Priority(1)
    @ApplicationScoped
    @ModelName("juiz")
    static class ModeloDoJuizQueAnota implements ChatModel {

        private final List<List<ChatMessage>> chamadas = new ArrayList<>();

        @Override
        public ChatResponse chat(ChatRequest request) {
            chamadas.add(List.copyOf(request.messages()));
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("{\"aprovada\": false, \"motivo\": \"" + MOTIVO + "\"}"))
                    .build();
        }

        /** As mensagens de cada chamada, na ordem. Métodos, porque o bean é acessado por proxy. */
        List<List<ChatMessage>> chamadas() {
            return chamadas;
        }

        void esquecer() {
            chamadas.clear();
        }
    }
}
