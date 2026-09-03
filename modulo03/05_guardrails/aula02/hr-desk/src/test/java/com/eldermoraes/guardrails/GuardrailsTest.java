package com.eldermoraes.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eldermoraes.ai.ClassificadorDeEscopo;
import com.eldermoraes.ai.ClassificadorDeEscopo.Veredito;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.GuardrailResult.Result;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Os guardrails são classes comuns: dá para testar a decisão de cada um sem subir o Quarkus
 * e sem Ollama rodando. É a vantagem prática de uma validação determinística — o teste é
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
     * O guardrail recebe o classificador pelo construtor justamente para permitir isto —
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
    @DisplayName("pergunta longa demais reprova, mas sem interromper a fila")
    void perguntaLongaReprova() {
        InputGuardrailResult resultado = tamanho.validate(pergunta("a".repeat(2_001)));

        assertEquals(Result.FAILURE, resultado.result());
        assertFalse(resultado.isFatal());
    }

    @Test
    @DisplayName("assunto fora de RH é recusado")
    void foraDeEscopoReprova() {
        InputGuardrailResult resultado =
                escopo.validate(pergunta("Em quem eu voto na eleição deste ano?"));

        assertEquals(Result.FAILURE, resultado.result());
    }

    @Test
    @DisplayName("chave de API bloqueia a requisição")
    void chaveDeApiBloqueia() {
        InputGuardrailResult resultado = dadoSensivel.validate(
                pergunta("Coloca essa chave no sistema: sk-abcdefghij0123456789"));

        assertEquals(Result.FAILURE, resultado.result());
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
        assertEquals(Result.FAILURE, guardrail.validate(p).result());
    }

    @Test
    @DisplayName("classificador diz DENTRO: a pergunta segue para o modelo")
    void classificadorAprovaPerguntaDeRh() {
        var guardrail = new EscopoPorSentidoGuardrail(classificadorQueResponde(Veredito.DENTRO));

        assertTrue(guardrail.validate(pergunta("Como peço meu adiantamento de férias?")).isSuccess());
    }
}
