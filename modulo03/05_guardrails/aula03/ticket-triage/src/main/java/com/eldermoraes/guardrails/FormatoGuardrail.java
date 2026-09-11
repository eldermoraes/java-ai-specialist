package com.eldermoraes.guardrails;

import com.eldermoraes.ai.Triagem;
import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * Primeiro guardrail da fila: garante que a resposta é um JSON de triagem.
 *
 * Ele vem primeiro porque os outros dois precisam da resposta já lida: não dá para validar
 * o campo prioridade antes de saber que existe um campo prioridade.
 *
 * As três reprovações daqui mostram reações diferentes para erros diferentes:
 *
 * - resposta vazia devolve retry: não há o que dizer ao modelo, só refazer a chamada;
 * - resposta fora do formato devolve reprompt: o modelo corrige quando avisado, e a
 *   instrução de formato entra na conversa junto com o motivo;
 * - campo obrigatório ausente também devolve reprompt, nomeando o campo que faltou.
 *
 * Há ainda uma quarta saída, que não é aprovar nem reprovar: quando o modelo embrulha o
 * JSON numa cerca de código, o guardrail extrai o JSON e devolve a resposta reescrita.
 * Quem roda depois já recebe a versão limpa.
 */
@ApplicationScoped
public class FormatoGuardrail implements OutputGuardrail {

    private static final Logger LOG = Logger.getLogger(FormatoGuardrail.class);

    /** A instrução que vai junto do motivo no reprompt. É o formato repetido, sem rodeio. */
    private static final String INSTRUCAO_DE_FORMATO =
            "Responda apenas com um JSON com os campos categoria, prioridade e resumo, "
                    + "sem texto fora do JSON.";

    /** Cerca de código em volta do JSON, com ou sem a marca da linguagem. */
    private static final Pattern CERCA_DE_CODIGO = Pattern.compile("(?s)```(?:json)?\\s*(.*?)```");

    @Override
    public OutputGuardrailResult validate(AiMessage resposta) {
        String texto = resposta.text();

        if (texto == null || texto.isBlank()) {
            LOG.warn("Formato: RETRY, resposta vazia. A mesma chamada é refeita");
            return retry("Resposta vazia.");
        }

        String json = semCercaDeCodigo(texto);

        Triagem triagem;
        try {
            triagem = Triagem.deJson(json);
        } catch (JsonProcessingException e) {
            LOG.warn("Formato: REPROMPT, resposta fora do formato JSON");
            return reprompt("A resposta não é um JSON de triagem.", INSTRUCAO_DE_FORMATO);
        }

        String ausente = campoAusente(triagem);
        if (ausente != null) {
            LOG.warnf("Formato: REPROMPT, campo obrigatório ausente: '%s'", ausente);
            return reprompt("A resposta não traz o campo " + ausente + ".", INSTRUCAO_DE_FORMATO);
        }

        if (!json.equals(texto)) {
            LOG.info("Formato: SUCCESS_WITH_RESULT, JSON extraído da cerca de código");
            return successWith(json);
        }

        LOG.info("Formato: OK, JSON com os três campos");
        return success();
    }

    /** Devolve só o JSON quando ele vem dentro de uma cerca de código, ou o texto original. */
    private static String semCercaDeCodigo(String texto) {
        var cerca = CERCA_DE_CODIGO.matcher(texto);
        return cerca.find() ? cerca.group(1).trim() : texto;
    }

    /** Nome do primeiro campo obrigatório que veio nulo ou em branco, ou null se todos vieram. */
    private static String campoAusente(Triagem triagem) {
        if (emBranco(triagem.categoria())) {
            return "categoria";
        }
        if (emBranco(triagem.prioridade())) {
            return "prioridade";
        }
        if (emBranco(triagem.resumo())) {
            return "resumo";
        }
        return null;
    }

    private static boolean emBranco(String valor) {
        return valor == null || valor.isBlank();
    }
}
