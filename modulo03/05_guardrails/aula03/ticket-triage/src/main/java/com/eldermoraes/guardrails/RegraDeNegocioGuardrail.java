package com.eldermoraes.guardrails;

import com.eldermoraes.ai.Triagem;
import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * Segundo guardrail da fila: o campo prioridade precisa ser um dos três valores que a fila
 * de atendimento conhece. Formato certo com valor inventado não serve para nada.
 *
 * Aqui estão as duas reações que o mesmo campo pede, conforme o tipo de erro:
 *
 * - "Alta" e "média" são o valor certo escrito de outro jeito. O código normaliza e a
 *   resposta segue, sem nova chamada;
 * - "urgente" fica fora da lista, e nenhuma regra de código traduz "urgente" para um dos
 *   três valores, então vai um reprompt com os valores permitidos.
 */
@ApplicationScoped
public class RegraDeNegocioGuardrail implements OutputGuardrail {

    private static final Logger LOG = Logger.getLogger(RegraDeNegocioGuardrail.class);

    /** Os valores que a fila de atendimento conhece. */
    private static final List<String> PRIORIDADES = List.of("baixa", "media", "alta");

    private static final Pattern ACENTO = Pattern.compile("\\p{M}");

    @Override
    public OutputGuardrailResult validate(AiMessage resposta) {
        String texto = resposta.text();

        Triagem triagem;
        try {
            triagem = Triagem.deJson(texto);
        } catch (JsonProcessingException e) {
            // O guardrail anterior já conferiu o formato, então cair aqui é sinal de defeito,
            // não de resposta ruim. Failure encerra a requisição e a aplicação trata.
            LOG.warn("Regra: FAILURE, não foi possível ler o JSON da resposta");
            return failure("Não foi possível ler o JSON da resposta.", e);
        }

        String prioridade = triagem.prioridade();
        String normalizada = normalizar(prioridade);

        if (!PRIORIDADES.contains(normalizada)) {
            LOG.warnf("Regra: REPROMPT, prioridade fora do conjunto: '%s'", prioridade);
            return reprompt("A prioridade '" + prioridade + "' não é um valor permitido.",
                    "Use exatamente um destes valores em prioridade: baixa, media, alta.");
        }

        if (!normalizada.equals(prioridade)) {
            LOG.infof("Regra: SUCCESS_WITH_RESULT, prioridade normalizada de '%s' para '%s'",
                    prioridade, normalizada);
            return successWith(new Triagem(triagem.categoria(), normalizada, triagem.resumo())
                    .paraJson());
        }

        LOG.info("Regra: OK, prioridade dentro do conjunto");
        return success();
    }

    /** Caixa e acento não mudam o valor: "Alta" e "média" são "alta" e "media". */
    private static String normalizar(String prioridade) {
        String semAcento = ACENTO.matcher(Normalizer.normalize(prioridade, Normalizer.Form.NFD))
                .replaceAll("");
        return semAcento.trim().toLowerCase(Locale.ROOT);
    }
}
