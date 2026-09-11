package com.eldermoraes.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eldermoraes.ai.TriagemDeChamados;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkus.test.junit.QuarkusTest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O teste anterior cobre a decisão de cada guardrail. Este cobre o que acontece depois
 * dela: quem refaz a chamada ao modelo, o que vai junto e quando as tentativas acabam.
 *
 * A fila é a declarada no AI Service e o laço de tentativas é o do framework. O que entra
 * no lugar do modelo é um executor de roteiro: uma lista de respostas combinadas, que
 * anota as mensagens recebidas em cada chamada. Nenhum prompt sai daqui, então o teste
 * roda sem Ollama e o resultado é sempre o mesmo.
 *
 * O Quarkus sobe porque a extensão substitui a configuração de guardrails por uma que lê
 * o application.properties, e essa configuração só existe com a aplicação de pé. O limite
 * de cada teste é declarado no próprio teste, para não depender do valor configurado.
 */
@QuarkusTest
class FilaDeSaidaTest {

    private static final String PROSA =
            "A VPN instável é um problema de rede e a prioridade é alta.";

    private static final String TRIAGEM_VALIDA = """
            {"categoria":"Rede","prioridade":"alta","resumo":"VPN cai desde ontem."}""";

    private static final String TRIAGEM_COM_CPF = """
            {"categoria":"Folha","prioridade":"Média",\
            "resumo":"O CPF 123.456.789-00 não emite holerite."}""";

    private static final String TRIAGEM_TRATADA = """
            {"categoria":"Folha","prioridade":"media",\
            "resumo":"O CPF [CPF] não emite holerite."}""";

    private static final String INSTRUCAO_DE_FORMATO =
            "Responda apenas com um JSON com os campos categoria, prioridade e resumo, "
                    + "sem texto fora do JSON.";

    @Test
    @DisplayName("prosa e depois JSON: duas chamadas, e a segunda leva a instrução de formato")
    void repromptRefazAChamadaComOMotivo() throws Exception {
        var modelo = new ModeloDeRoteiro(TRIAGEM_VALIDA);
        var request = requisicao(PROSA, modelo);

        var resultado = executor(2).execute(request);

        assertEquals(2, modelo.chamadasAoModelo());
        assertEquals(INSTRUCAO_DE_FORMATO, ultimaMensagemDaSegundaChamada(modelo));
        assertEquals(TRIAGEM_VALIDA, resultado.<ChatResponse>response(request).aiMessage().text());
    }

    @Test
    @DisplayName("resposta vazia e depois JSON: duas chamadas, e a segunda não leva mensagem nova")
    void retryRefazAMesmaChamada() throws Exception {
        var modelo = new ModeloDeRoteiro(TRIAGEM_VALIDA);
        var request = requisicao("", modelo);

        var resultado = executor(2).execute(request);

        assertEquals(2, modelo.chamadasAoModelo());
        assertTrue(modelo.mensagensDaChamada(1).isEmpty());
        assertEquals(TRIAGEM_VALIDA, resultado.<ChatResponse>response(request).aiMessage().text());
    }

    @Test
    @DisplayName("com limite 1 a reprovação vira exceção, e a prosa não é devolvida")
    void limiteEsgotadoViraExcecao() throws Exception {
        var modelo = new ModeloDeRoteiro(PROSA);
        var request = requisicao(PROSA, modelo);
        var executor = executor(1);

        var erro = assertThrows(OutputGuardrailException.class, () -> executor.execute(request));

        assertEquals(1, modelo.chamadasAoModelo());
        assertFalse(erro.getMessage().contains(PROSA));
        assertTrue(erro.getMessage().contains("A resposta não é um JSON de triagem."));
    }

    @Test
    @DisplayName("a reescrita feita dentro da fila chega à aplicação, numa só chamada")
    void reescritaDaFilaChegaNaAplicacao() throws Exception {
        var modelo = new ModeloDeRoteiro();
        var request = requisicao("```json\n" + TRIAGEM_COM_CPF + "\n```", modelo);

        var resultado = executor(2).execute(request);

        assertEquals(1, modelo.chamadasAoModelo());
        assertEquals(TRIAGEM_TRATADA,
                resultado.<ChatResponse>response(request).aiMessage().text());
    }

    /** A fila que o AI Service declara, na ordem em que está declarada. */
    private static List<OutputGuardrail> filaDoAiService() throws Exception {
        var declarados = TriagemDeChamados.class.getMethod("triar", String.class)
                .getAnnotation(OutputGuardrails.class)
                .value();
        return Arrays.stream(declarados).map(FilaDeSaidaTest::instanciar).toList();
    }

    private static OutputGuardrail instanciar(Class<? extends OutputGuardrail> tipo) {
        try {
            return tipo.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Não foi possível instanciar " + tipo, e);
        }
    }

    /**
     * O laço de tentativas do framework, com o limite declarado pelo teste. O limite conta
     * chamadas ao modelo: 1 é só a chamada original, 2 é a original mais uma tentativa.
     */
    private static OutputGuardrailExecutor executor(int limiteDeChamadas) throws Exception {
        OutputGuardrailsConfig limite = () -> limiteDeChamadas;
        return OutputGuardrailExecutor.builder()
                .config(limite)
                .guardrails(filaDoAiService())
                .build();
    }

    private static OutputGuardrailRequest requisicao(String primeiraResposta, ChatExecutor modelo) {
        return OutputGuardrailRequest.builder()
                .responseFromLLM(ChatResponse.builder()
                        .aiMessage(AiMessage.from(primeiraResposta))
                        .build())
                .chatExecutor(modelo)
                .requestParams(GuardrailRequestParams.builder()
                        .userMessageTemplate("{chamado}")
                        .variables(Map.of())
                        .invocationContext(InvocationContext.builder()
                                .interfaceName(TriagemDeChamados.class.getName())
                                .methodName("triar")
                                .build())
                        .build())
                .build();
    }

    private static String ultimaMensagemDaSegundaChamada(ModeloDeRoteiro modelo) {
        var mensagens = modelo.mensagensDaChamada(1);
        return ((UserMessage) mensagens.getLast()).singleText();
    }

    /**
     * Entra no lugar do modelo: devolve as respostas combinadas, na ordem, e guarda as
     * mensagens que recebeu em cada chamada.
     */
    private static final class ModeloDeRoteiro implements ChatExecutor {

        private final Deque<String> roteiro;
        private final List<List<ChatMessage>> chamadas = new ArrayList<>();

        ModeloDeRoteiro(String... respostas) {
            this.roteiro = new ArrayDeque<>(List.of(respostas));
        }

        @Override
        public ChatResponse execute() {
            return execute(List.of());
        }

        @Override
        public ChatResponse execute(List<ChatMessage> mensagens) {
            chamadas.add(List.copyOf(mensagens));
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(roteiro.poll()))
                    .build();
        }

        /** A chamada original mais as tentativas que este executor atendeu. */
        int chamadasAoModelo() {
            return 1 + chamadas.size();
        }

        List<ChatMessage> mensagensDaChamada(int indice) {
            return chamadas.get(indice - 1);
        }
    }
}
