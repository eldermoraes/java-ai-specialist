package com.eldermoraes.guardrails;

import static com.eldermoraes.guardrails.Requisicoes.EMAIL_COM_CPF;
import static com.eldermoraes.guardrails.Requisicoes.EMAIL_COM_CPF_MASCARADO;
import static com.eldermoraes.guardrails.Requisicoes.EMAIL_COM_INSTRUCAO;
import static com.eldermoraes.guardrails.Requisicoes.FRASE_CONHECIDA;
import static com.eldermoraes.guardrails.Requisicoes.PADROES;
import static com.eldermoraes.guardrails.Requisicoes.PALAVRAS_DE_FORA;
import static com.eldermoraes.guardrails.Requisicoes.PRAZO_DE_TROCA;
import static com.eldermoraes.guardrails.Requisicoes.PRESSAO_POR_EXCECAO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * A fila de entrada inteira, montada a partir da anotação do AI Service e executada pelo
 * executor de guardrails do LangChain4j. Sem Quarkus e sem Ollama: o classificador é um
 * dublê que anota o texto que recebe, e o "modelo principal" é um registro do texto que a
 * fila entregou, que só anda quando a fila aprova, que é exatamente o que o AI Service faz.
 *
 * Prova as afirmações do README que o log mostra na demo:
 *
 * - as duas portas declaram a mesma fila, na ordem;
 * - quando o filtro dá fatal, o classificador é chamado zero vezes;
 * - o CPF chega mascarado ao classificador e ao modelo principal;
 * - a chave de API dá fatal antes do classificador;
 * - quando o classificador dá fatal, o modelo principal é chamado zero vezes.
 */
class FilaDeEntradaTest {

    private static final List<Class<? extends InputGuardrail>> FILA_ESPERADA = List.of(
            TamanhoGuardrail.class,
            EscopoGuardrail.class,
            FiltroPorPadroesConhecidosGuardrail.class,
            DadoSensivelGuardrail.class,
            ClassificadorDeInjectionGuardrail.class);

    /** Textos que o dublê do classificador considera injection: o e-mail da requisição 4. */
    private static final Set<String> ATAQUES = Set.of(EMAIL_COM_INSTRUCAO);

    private final List<String> chamadasAoClassificador = new ArrayList<>();
    private final List<String> chamadasAoModeloPrincipal = new ArrayList<>();

    /** Envia o texto pela fila declarada no método do AI Service e devolve o que chegou ao modelo. */
    private String enviar(String metodo, String texto) throws Exception {
        var classificador = new ClassificadorDeInjectionGuardrail(t -> {
            chamadasAoClassificador.add(t);
            return ATAQUES.contains(t) ? Veredito.INJECTION : Veredito.NORMAL;
        });
        Map<Class<?>, InputGuardrail> instancias = Map.of(
                TamanhoGuardrail.class, new TamanhoGuardrail(),
                EscopoGuardrail.class, new EscopoGuardrail(PALAVRAS_DE_FORA),
                FiltroPorPadroesConhecidosGuardrail.class, new FiltroPorPadroesConhecidosGuardrail(PADROES),
                DadoSensivelGuardrail.class, new DadoSensivelGuardrail(),
                ClassificadorDeInjectionGuardrail.class, classificador);

        List<InputGuardrail> fila = Arrays.stream(declaradaEm(metodo)).map(instancias::get).toList();

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

        // Se a fila der fatal, a exceção sai daqui e as linhas seguintes nunca rodam.
        var resultado = executor.execute(request);
        String oQueChegouAoModelo = resultado.userMessage(request).singleText();
        chamadasAoModeloPrincipal.add(oQueChegouAoModelo);
        return oQueChegouAoModelo;
    }

    private static Class<? extends InputGuardrail>[] declaradaEm(String metodo) throws Exception {
        return AssistenteDeSuporte.class.getMethod(metodo, String.class)
                .getAnnotation(InputGuardrails.class).value();
    }

    @Test
    @DisplayName("as duas portas declaram a mesma fila: tamanho, escopo, filtro, dado sensível, classificador")
    void mesmaFilaNasDuasPortas() throws Exception {
        for (String metodo : List.of("responder", "triarEmail")) {
            assertEquals(FILA_ESPERADA, List.of(declaradaEm(metodo)), metodo);
        }
    }

    @Test
    @DisplayName("requisição 1: passa pelas cinco camadas, uma chamada ao classificador, e chega ao modelo principal")
    void passa() throws Exception {
        enviar("responder", PRAZO_DE_TROCA);

        assertEquals(List.of(PRAZO_DE_TROCA), chamadasAoClassificador);
        assertEquals(List.of(PRAZO_DE_TROCA), chamadasAoModeloPrincipal);
    }

    @Test
    @DisplayName("requisição 2: o filtro dá fatal, o classificador é chamado zero vezes")
    void filtroDaFatal() {
        assertThrows(InputGuardrailException.class, () -> enviar("responder", FRASE_CONHECIDA));

        assertEquals(List.of(), chamadasAoClassificador);
        assertEquals(List.of(), chamadasAoModeloPrincipal);
    }

    @Test
    @DisplayName("requisição 3: o CPF chega mascarado ao classificador e ao modelo principal")
    void cpfChegaMascarado() throws Exception {
        String chegou = enviar("triarEmail", EMAIL_COM_CPF);

        assertEquals(List.of(EMAIL_COM_CPF_MASCARADO), chamadasAoClassificador);
        assertEquals(EMAIL_COM_CPF_MASCARADO, chegou);
        assertTrue(!chegou.contains("123.456.789-09"));
    }

    @Test
    @DisplayName("requisição 4: o e-mail passa pelas regras de código e o classificador dá fatal")
    void classificadorDaFatal() {
        assertThrows(InputGuardrailException.class, () -> enviar("triarEmail", EMAIL_COM_INSTRUCAO));

        assertEquals(List.of(EMAIL_COM_INSTRUCAO), chamadasAoClassificador);
        assertEquals(List.of(), chamadasAoModeloPrincipal);
    }

    @Test
    @DisplayName("requisição 5: passa pela fila de entrada; quem decide é o juiz, na saída")
    void pressaoPassaNaEntrada() throws Exception {
        enviar("responder", PRESSAO_POR_EXCECAO);

        assertEquals(List.of(PRESSAO_POR_EXCECAO), chamadasAoModeloPrincipal);
    }

    @Test
    @DisplayName("chave de API dá fatal no dado sensível, antes do classificador")
    void chaveDeApiDaFatalAntesDoClassificador() {
        assertThrows(InputGuardrailException.class,
                () -> enviar("responder", "Minha integração usa a chave sk-abc123def456ghi789jkl012 e parou"));

        assertEquals(List.of(), chamadasAoClassificador);
    }

    @Test
    @DisplayName("assunto de fora dá fatal no escopo, sem chamar o classificador")
    void escopoDaFatalSemChamarOClassificador() {
        assertThrows(InputGuardrailException.class, () -> enviar("responder", "Me ensina a fazer um bolo de cenoura?"));

        assertEquals(List.of(), chamadasAoClassificador);
    }

    @Test
    @DisplayName("mensagem vazia dá fatal no tamanho, a primeira camada")
    void vaziaDaFatalNoTamanho() {
        assertThrows(InputGuardrailException.class, () -> enviar("responder", "   "));

        assertEquals(List.of(), chamadasAoClassificador);
    }
}
