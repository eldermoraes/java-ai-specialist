package com.eldermoraes.guardrails;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * Terceiro guardrail da fila: o CPF que veio no chamado costuma voltar dentro do resumo,
 * porque o modelo repete o que veio no chamado. Como o número não acrescenta nada à
 * triagem, ele é apagado da resposta.
 *
 * É a reação mais barata de todas: o código corrige e a resposta segue, sem nova chamada
 * ao modelo. Reprovar aqui seria pagar uma pergunta inteira por um problema que uma
 * expressão regular resolve.
 *
 * Expressão regular alcança o que tem formato fixo. Dado sensível escrito por extenso não
 * é alcançado assim.
 *
 * Por ser o último da fila, ele devolve sempre a resposta que aprovou, mascarada ou não.
 * O motivo está na ficha técnica do README: a resposta que a aplicação recebe é a que o
 * último resultado carrega, então quem fecha a fila entrega o texto que validou.
 */
@ApplicationScoped
public class DadoSensivelGuardrail implements OutputGuardrail {

    private static final Logger LOG = Logger.getLogger(DadoSensivelGuardrail.class);

    /** CPF com ou sem pontuação: 000.000.000-00 ou 00000000000. */
    private static final Pattern CPF = Pattern.compile(
            "\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");

    @Override
    public OutputGuardrailResult validate(AiMessage resposta) {
        String texto = resposta.text();
        String mascarado = CPF.matcher(texto).replaceAll("[CPF]");

        if (!mascarado.equals(texto)) {
            LOG.info("Dado sensível: SUCCESS_WITH_RESULT, CPF mascarado. A resposta segue");
        } else {
            LOG.info("Dado sensível: SUCCESS_WITH_RESULT, nada encontrado. A resposta segue inteira");
        }

        return successWith(mascarado);
    }
}
