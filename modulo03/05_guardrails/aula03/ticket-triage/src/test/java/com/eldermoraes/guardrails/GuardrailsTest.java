package com.eldermoraes.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.GuardrailResult.Result;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Os guardrails são classes comuns: dá para testar a decisão de cada um sem subir o Quarkus
 * e sem Ollama rodando. É a vantagem prática de uma validação determinística: o teste é
 * rápido e o resultado é sempre o mesmo.
 *
 * Uma decisão por teste, com a resposta do modelo montada à mão.
 */
class GuardrailsTest {

    private final FormatoGuardrail formato = new FormatoGuardrail();
    private final RegraDeNegocioGuardrail regra = new RegraDeNegocioGuardrail();
    private final DadoSensivelGuardrail dadoSensivel = new DadoSensivelGuardrail();

    private static final String TRIAGEM_VALIDA = """
            {"categoria":"Rede","prioridade":"alta","resumo":"VPN cai desde ontem."}""";

    private static AiMessage resposta(String texto) {
        return AiMessage.from(texto);
    }

    @Test
    @DisplayName("resposta vazia pede retry: a mesma chamada é refeita")
    void respostaVaziaPedeRetry() {
        OutputGuardrailResult resultado = formato.validate(resposta("   "));

        assertTrue(resultado.isRetry());
        assertFalse(resultado.isReprompt());
    }

    @Test
    @DisplayName("resposta em prosa pede reprompt com a instrução de formato")
    void respostaEmProsaPedeReprompt() {
        OutputGuardrailResult resultado = formato.validate(
                resposta("A VPN instável é um problema de rede e a prioridade é alta."));

        assertTrue(resultado.isReprompt());
        assertEquals("Responda apenas com um JSON com os campos categoria, prioridade e "
                + "resumo, sem texto fora do JSON.", resultado.getReprompt().orElseThrow());
    }

    @Test
    @DisplayName("JSON sem o campo resumo pede reprompt nomeando o campo")
    void campoAusentePedeReprompt() {
        OutputGuardrailResult resultado = formato.validate(
                resposta("""
                        {"categoria":"Rede","prioridade":"alta"}"""));

        assertTrue(resultado.isReprompt());
        assertTrue(resultado.failures().getFirst().message().contains("resumo"));
    }

    @Test
    @DisplayName("JSON dentro de cerca de código segue reescrito, sem a cerca")
    void cercaDeCodigoEhRemovida() {
        OutputGuardrailResult resultado = formato.validate(
                resposta("```json\n" + TRIAGEM_VALIDA + "\n```"));

        assertEquals(Result.SUCCESS_WITH_RESULT, resultado.result());
        assertEquals(TRIAGEM_VALIDA, resultado.successfulText());
    }

    @Test
    @DisplayName("prioridade 'Alta' é normalizada pelo código, sem nova chamada")
    void prioridadeEhNormalizada() {
        OutputGuardrailResult resultado = regra.validate(
                resposta("""
                        {"categoria":"Rede","prioridade":"Alta","resumo":"VPN instável."}"""));

        assertEquals(Result.SUCCESS_WITH_RESULT, resultado.result());
        assertTrue(resultado.successfulText().contains("\"prioridade\":\"alta\""));
    }

    @Test
    @DisplayName("prioridade fora do conjunto pede reprompt com os valores permitidos")
    void prioridadeInvalidaPedeReprompt() {
        OutputGuardrailResult resultado = regra.validate(
                resposta("""
                        {"categoria":"Rede","prioridade":"urgente","resumo":"VPN instável."}"""));

        assertTrue(resultado.isReprompt());
        assertEquals("Use exatamente um destes valores em prioridade: baixa, media, alta.",
                resultado.getReprompt().orElseThrow());
    }

    @Test
    @DisplayName("CPF na resposta é mascarado pelo código, sem nova chamada")
    void cpfEhMascarado() {
        OutputGuardrailResult resultado = dadoSensivel.validate(
                resposta("""
                        {"categoria":"Folha","prioridade":"media",\
                        "resumo":"O CPF 123.456.789-00 não emite holerite."}"""));

        assertEquals(Result.SUCCESS_WITH_RESULT, resultado.result());
        assertTrue(resultado.successfulText().contains("[CPF]"));
        assertFalse(resultado.successfulText().contains("123.456.789-00"));
    }

    @Test
    @DisplayName("triagem válida passa pela fila e chega inteira ao fim dela")
    void triagemValidaPassa() {
        AiMessage r = resposta(TRIAGEM_VALIDA);

        assertEquals(Result.SUCCESS, formato.validate(r).result());
        assertEquals(Result.SUCCESS, regra.validate(r).result());
        assertEquals(TRIAGEM_VALIDA, dadoSensivel.validate(r).successfulText());
    }
}
