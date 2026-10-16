package com.eldermoraes.guardrails;

import com.eldermoraes.dto.Triagem;
import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * A fila de saída da triagem do e-mail, num guardrail só: formato e regra de negócio.
 *
 * A aplicação vai ler os campos da triagem, então a saída é estruturada e o código valida
 * antes de ela chegar a alguém. As reações, uma por tipo de erro:
 *
 * - resposta vazia, o guardrail pede retry: não há o que dizer ao modelo, só refazer;
 * - fora do formato JSON e/ou campo ausente, o guardrail pede reprompt com a instrução
 *   de formato;
 * - categoria ou prioridade fora do conjunto que a fila de atendimento conhece, o
 *   guardrail pede reprompt com os valores permitidos;
 * - JSON dentro de bloco de código e/ou diferença de caixa ("Média", "Troca"), o código
 *   reescreve, sem nova chamada.
 *
 * Formato e regra de negócio estão na mesma classe por um fato do framework: depois que
 * um guardrail reescreve a resposta, o LangChain4j não deixa o guardrail seguinte pedir
 * reprompt. Se o formato extraísse o JSON do bloco de código e a regra de negócio, num
 * segundo guardrail, encontrasse um valor fora do conjunto, o reprompt dela seria
 * bloqueado e a requisição cairia no fallback. Por isso este guardrail valida tudo, o
 * formato e o conjunto de valores, antes de reescrever: só reescreve quando a triagem
 * já está aprovada. FilaDeSaidaTest documenta o bloqueio e prova que esta ordem o evita.
 *
 * O CPF do e-mail já foi mascarado na entrada; aqui não há dado sensível para tratar.
 */
@ApplicationScoped
public class FormatoDaTriagemGuardrail implements OutputGuardrail {

    private static final Logger LOG = Logger.getLogger(FormatoDaTriagemGuardrail.class);

    /** A instrução que vai junto do motivo no reprompt de formato. É o formato repetido, sem rodeio. */
    static final String INSTRUCAO_DE_FORMATO =
            "Responda apenas com um JSON com os campos categoria, prioridade e resumo, "
                    + "sem texto fora do JSON.";

    static final String INSTRUCAO_DE_CATEGORIA =
            "Use exatamente um destes valores em categoria: " + String.join(", ", Triagem.CATEGORIAS) + ".";

    static final String INSTRUCAO_DE_PRIORIDADE =
            "Use exatamente um destes valores em prioridade: " + String.join(", ", Triagem.PRIORIDADES) + ".";

    /** Bloco de código em volta do JSON, com ou sem a marca da linguagem. */
    private static final Pattern BLOCO_DE_CODIGO = Pattern.compile("(?s)```(?:json)?\\s*(.*?)```");

    @Override
    public OutputGuardrailResult validate(AiMessage resposta) {
        String texto = resposta.text();

        if (texto == null || texto.isBlank()) {
            LOG.warn("Formato: RETRY, resposta vazia. A mesma chamada é refeita");
            return retry("Resposta vazia.");
        }

        String json = semBlocoDeCodigo(texto);

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

        // A regra de negócio: o conjunto de valores, conferido ANTES de qualquer reescrita.
        String categoria = Normalizacao.normalizar(triagem.categoria());
        if (!Triagem.CATEGORIAS.contains(categoria)) {
            LOG.warnf("Regra de negócio: REPROMPT, categoria fora do conjunto: '%s'", triagem.categoria());
            return reprompt("A categoria '" + triagem.categoria() + "' não é um valor permitido.",
                    INSTRUCAO_DE_CATEGORIA);
        }

        String prioridade = Normalizacao.normalizar(triagem.prioridade());
        if (!Triagem.PRIORIDADES.contains(prioridade)) {
            LOG.warnf("Regra de negócio: REPROMPT, prioridade fora do conjunto: '%s'", triagem.prioridade());
            return reprompt("A prioridade '" + triagem.prioridade() + "' não é um valor permitido.",
                    INSTRUCAO_DE_PRIORIDADE);
        }

        // Tudo válido. Só agora o código reescreve, se houver o que reescrever.
        boolean veioEmBlocoDeCodigo = !json.equals(texto);
        boolean caixaDiferente = !categoria.equals(triagem.categoria()) || !prioridade.equals(triagem.prioridade());

        if (caixaDiferente) {
            String normalizado = new Triagem(categoria, prioridade, triagem.resumo()).paraJson();
            LOG.infof("Formato: SUCCESS_WITH_RESULT, valores normalizados para '%s' e '%s'%s. A triagem segue",
                    categoria, prioridade, veioEmBlocoDeCodigo ? ", JSON extraído do bloco de código" : "");
            return successWith(normalizado);
        }

        if (veioEmBlocoDeCodigo) {
            LOG.info("Formato: SUCCESS_WITH_RESULT, JSON extraído do bloco de código. A triagem segue");
            return successWith(json);
        }

        LOG.info("Formato: OK, JSON com os três campos e valores dentro do conjunto. A triagem segue");
        return success();
    }

    /** Devolve só o JSON quando ele vem dentro de um bloco de código, ou o texto original. */
    private static String semBlocoDeCodigo(String texto) {
        var bloco = BLOCO_DE_CODIGO.matcher(texto);
        return bloco.find() ? bloco.group(1).trim() : texto;
    }

    /** Nome do primeiro campo obrigatório que veio nulo ou em branco, ou null se todos vieram. */
    private static String campoAusente(Triagem triagem) {
        for (var campo : List.of(
                new String[] {"categoria", triagem.categoria()},
                new String[] {"prioridade", triagem.prioridade()},
                new String[] {"resumo", triagem.resumo()})) {
            if (campo[1] == null || campo[1].isBlank()) {
                return campo[0];
            }
        }
        return null;
    }
}
