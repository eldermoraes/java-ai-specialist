package com.eldermoraes.guardrails;

import static com.eldermoraes.guardrails.Requisicoes.DESVIO;
import static com.eldermoraes.guardrails.Requisicoes.EMAIL_COM_INSTRUCAO;
import static com.eldermoraes.guardrails.Requisicoes.FRASE_CONHECIDA;
import static com.eldermoraes.guardrails.Requisicoes.PADROES;
import static com.eldermoraes.guardrails.Requisicoes.PARAFRASE;
import static com.eldermoraes.guardrails.Requisicoes.PRAZO_DE_TROCA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eldermoraes.ai.AssistenteDeSuporte;
import com.eldermoraes.ai.ClassificadorDeInjection.Veredito;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailExecutor;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.guardrail.InputGuardrails;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A fila inteira, montada a partir da anotação do AI Service e executada pelo executor de
 * guardrails do LangChain4j. Sem Quarkus e sem Ollama: o classificador é um dublê que conta
 * as chamadas que recebe, e o "modelo principal" é um contador que só anda quando a fila
 * aprova, que é exatamente o que o AI Service faz.
 *
 * Prova as duas afirmações do README que o log mostra na demo:
 *
 * - quando o filtro dá fatal, o classificador é chamado zero vezes;
 * - quando o classificador dá fatal, o modelo principal é chamado zero vezes.
 */
class FilaDeEntradaTest {

    /** Textos que o dublê do classificador considera injection: os ataques 3 e 4. */
    private static final Set<String> ATAQUES = Set.of(PARAFRASE, EMAIL_COM_INSTRUCAO);

    private final List<String> chamadasAoClassificador = new ArrayList<>();
    private final List<String> chamadasAoModeloPrincipal = new ArrayList<>();

    /** Envia o texto pela fila declarada no método do AI Service. */
    private String enviar(String metodo, String texto) throws Exception {
        var classificador = new ClassificadorDeInjectionGuardrail(t -> {
            chamadasAoClassificador.add(t);
            return ATAQUES.contains(t) ? Veredito.INJECTION : Veredito.NORMAL;
        });
        Map<Class<?>, InputGuardrail> instancias = Map.of(
                FiltroPorPadroesConhecidosGuardrail.class, new FiltroPorPadroesConhecidosGuardrail(PADROES),
                ClassificadorDeInjectionGuardrail.class, classificador);

        var declarada = AssistenteDeSuporte.class.getMethod(metodo, String.class)
                .getAnnotation(InputGuardrails.class).value();
        List<InputGuardrail> fila = Arrays.stream(declarada).map(instancias::get).toList();

        var executor = new InputGuardrailExecutor.InputGuardrailExecutorBuilder()
                .guardrails(fila).build();
        var request = InputGuardrailRequest.builder()
                .userMessage(UserMessage.from(texto))
                .commonParams(GuardrailRequestParams.builder()
                        .userMessageTemplate(texto).variables(Map.of())
                        .invocationContext(InvocationContext.builder()
                                .interfaceName(AssistenteDeSuporte.class.getName())
                                .methodName(metodo).build())
                        .build())
                .build();

        // Se a fila der fatal, a exceção sai daqui e a linha seguinte nunca roda.
        executor.execute(request);
        chamadasAoModeloPrincipal.add(texto);
        return "resposta do modelo";
    }

    @Test
    @DisplayName("as duas portas declaram a mesma fila, filtro antes do classificador")
    void mesmaFilaNasDuasPortas() throws Exception {
        var esperada = List.of(FiltroPorPadroesConhecidosGuardrail.class, ClassificadorDeInjectionGuardrail.class);
        for (String metodo : List.of("responder", "resumirEmail")) {
            var declarada = AssistenteDeSuporte.class.getMethod(metodo, String.class)
                    .getAnnotation(InputGuardrails.class).value();
            assertEquals(esperada, List.of(declarada), metodo);
        }
    }

    @Test
    @DisplayName("requisição 1: passa pelas duas camadas e chega ao modelo principal")
    void passa() throws Exception {
        enviar("responder", PRAZO_DE_TROCA);

        assertEquals(List.of(PRAZO_DE_TROCA), chamadasAoClassificador);
        assertEquals(List.of(PRAZO_DE_TROCA), chamadasAoModeloPrincipal);
    }

    @Test
    @DisplayName("requisição 2: o filtro dá fatal e o classificador é chamado zero vezes")
    void filtroDaFatal() {
        assertThrows(InputGuardrailException.class, () -> enviar("responder", FRASE_CONHECIDA));

        assertEquals(List.of(), chamadasAoClassificador);
        assertEquals(List.of(), chamadasAoModeloPrincipal);
    }

    @Test
    @DisplayName("requisição 3: o classificador dá fatal e o modelo principal é chamado zero vezes")
    void classificadorDaFatal() {
        assertThrows(InputGuardrailException.class, () -> enviar("responder", PARAFRASE));

        assertEquals(List.of(PARAFRASE), chamadasAoClassificador);
        assertEquals(List.of(), chamadasAoModeloPrincipal);
    }

    @Test
    @DisplayName("requisição 4: o e-mail passa pelo filtro e o classificador dá fatal")
    void indiretaClassificadorDaFatal() {
        assertThrows(InputGuardrailException.class, () -> enviar("resumirEmail", EMAIL_COM_INSTRUCAO));

        assertEquals(List.of(EMAIL_COM_INSTRUCAO), chamadasAoClassificador);
        assertEquals(List.of(), chamadasAoModeloPrincipal);
    }

    @Test
    @DisplayName("requisição 5: o desvio passa pelas duas camadas")
    void desvioPassa() throws Exception {
        enviar("responder", DESVIO);

        assertEquals(List.of(DESVIO), chamadasAoClassificador);
        assertEquals(List.of(DESVIO), chamadasAoModeloPrincipal);
    }
}
