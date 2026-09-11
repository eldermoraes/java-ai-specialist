package com.eldermoraes.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eldermoraes.ai.AssistenteRh;
import com.eldermoraes.ai.ClassificadorDeEscopo;
import com.eldermoraes.ai.ClassificadorDeEscopo.Veredito;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailResult.Result;
import dev.langchain4j.guardrail.InputGuardrailResult;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.InputGuardrailExecutor;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.invocation.InvocationContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Os guardrails são classes comuns: dá para testar a decisão de cada um sem subir o Quarkus
 * e sem Ollama rodando. É a vantagem prática de uma validação determinística: o teste é
 * rápido e o resultado é sempre o mesmo.
 */
class GuardrailsTest {

    private final TamanhoGuardrail tamanho = new TamanhoGuardrail();
    private final EscopoGuardrail escopo = new EscopoGuardrail();
    private final DadoSensivelGuardrail dadoSensivel = new DadoSensivelGuardrail();

    private static UserMessage pergunta(String texto) {
        return UserMessage.from(texto);
    }

    /**
     * Dublê do classificador: devolve o veredito combinado, sem chamar modelo nenhum.
     * O guardrail recebe o classificador pelo construtor justamente para permitir isto:
     * testar a decisão dele sem depender de uma resposta não determinística.
     */
    private static ClassificadorDeEscopo classificadorQueResponde(Veredito veredito) {
        return pergunta -> veredito;
    }

    @Test
    @DisplayName("pergunta comum de RH passa pelos três guardrails")
    void perguntaComumPassa() {
        UserMessage p = pergunta("Quantos dias de férias eu tenho acumulados?");

        assertTrue(tamanho.validate(p).isSuccess());
        assertTrue(escopo.validate(p).isSuccess());
        assertTrue(dadoSensivel.validate(p).isSuccess());
    }

    @Test
    @DisplayName("pergunta em branco é fatal: a fila para e os seguintes não rodam")
    void perguntaEmBrancoEhFatal() {
        InputGuardrailResult resultado = tamanho.validate(pergunta("   "));

        assertEquals(Result.FATAL, resultado.result());
        assertTrue(resultado.isFatal());
    }

    @Test
    @DisplayName("pergunta longa demais é fatal: interrompe a fila")
    void perguntaLongaReprova() {
        InputGuardrailResult resultado = tamanho.validate(pergunta("a".repeat(2_001)));

        assertEquals(Result.FATAL, resultado.result());
        assertTrue(resultado.isFatal());
    }

    @Test
    @DisplayName("assunto fora de RH é recusado")
    void foraDeEscopoReprova() {
        InputGuardrailResult resultado =
                escopo.validate(pergunta("Em quem eu voto na eleição deste ano?"));

        assertEquals(Result.FATAL, resultado.result());
    }

    @Test
    @DisplayName("chave de API bloqueia a requisição")
    void chaveDeApiBloqueia() {
        InputGuardrailResult resultado = dadoSensivel.validate(
                pergunta("Coloca essa chave no sistema: sk-abcdefghij0123456789"));

        assertEquals(Result.FATAL, resultado.result());
    }

    @Test
    @DisplayName("CPF é mascarado e a pergunta segue reescrita")
    void cpfEhMascarado() {
        InputGuardrailResult resultado = dadoSensivel.validate(
                pergunta("Confere as férias do CPF 123.456.789-00, por favor"));

        assertEquals(Result.SUCCESS_WITH_RESULT, resultado.result());
        assertTrue(resultado.isSuccess());
        assertTrue(resultado.successfulText().contains("[CPF]"));
        assertFalse(resultado.successfulText().contains("123.456.789-00"));
    }

    @Test
    @DisplayName("classificador diz FORA: a pergunta é recusada mesmo sem termo na lista")
    void classificadorReprovaForaDeEscopo() {
        var guardrail = new EscopoPorSentidoGuardrail(classificadorQueResponde(Veredito.FORA));
        UserMessage p = pergunta("Me ensina a fazer um bolo de cenoura?");

        // A regra de código deixa passar: nenhum termo da lista aparece na pergunta.
        assertTrue(escopo.validate(p).isSuccess());
        // O classificador entende o assunto e recusa.
        assertEquals(Result.FATAL, guardrail.validate(p).result());
    }

    @Test
    @DisplayName("classificador diz DENTRO: a pergunta segue para o modelo")
    void classificadorAprovaPerguntaDeRh() {
        var guardrail = new EscopoPorSentidoGuardrail(classificadorQueResponde(Veredito.DENTRO));

        assertTrue(guardrail.validate(pergunta("Como peço meu adiantamento de férias?")).isSuccess());
    }

    @ParameterizedTest
    @CsvSource({"vazia, 1", "longa, 1", "escopo, 2", "chave, 3", "sentido, 4"})
    @DisplayName("a primeira rejeição encerra a fila declarada no AI Service")
    void rejeicaoInterrompeFila(String caso, int quantidadeEsperada) throws Exception {
        String texto = switch (caso) {
            case "vazia" -> "   ";
            case "longa" -> "a".repeat(2_500);
            case "escopo" -> "Em quem eu voto na eleição deste ano?";
            case "chave" -> "Confira minha chave sk-abcdefghij0123456789";
            default -> "Me ensina a fazer um bolo de cenoura?";
        };
        var chamadasAoClassificador = new ArrayList<String>();
        var sentido = new EscopoPorSentidoGuardrail(pergunta -> {
            chamadasAoClassificador.add(pergunta);
            return Veredito.FORA;
        });
        Map<Class<?>, InputGuardrail> instancias = Map.of(
                TamanhoGuardrail.class, tamanho,
                EscopoGuardrail.class, escopo,
                DadoSensivelGuardrail.class, dadoSensivel,
                EscopoPorSentidoGuardrail.class, sentido);
        var ordem = AssistenteRh.class.getMethod("responder", String.class)
                .getAnnotation(InputGuardrails.class).value();
        var executados = new ArrayList<Class<?>>();
        var guardrails = Arrays.stream(ordem).map(tipo -> (InputGuardrail) new InputGuardrail() {
            @Override
            public InputGuardrailResult validate(UserMessage mensagem) {
                executados.add(tipo);
                return instancias.get(tipo).validate(mensagem);
            }
        }).toList();
        var executor = new InputGuardrailExecutor.InputGuardrailExecutorBuilder()
                .guardrails(guardrails).build();
        var request = InputGuardrailRequest.builder()
                .userMessage(pergunta(texto))
                .commonParams(GuardrailRequestParams.builder()
                        .userMessageTemplate(texto).variables(Map.of())
                        .invocationContext(InvocationContext.builder()
                                .interfaceName(AssistenteRh.class.getName())
                                .methodName("responder").build())
                        .build())
                .build();

        assertThrows(InputGuardrailException.class, () -> executor.execute(request));
        assertEquals(List.of(TamanhoGuardrail.class, EscopoGuardrail.class,
                DadoSensivelGuardrail.class, EscopoPorSentidoGuardrail.class)
                .subList(0, quantidadeEsperada), executados);
        assertEquals(caso.equals("sentido") ? List.of(texto) : List.of(), chamadasAoClassificador);
    }

}
