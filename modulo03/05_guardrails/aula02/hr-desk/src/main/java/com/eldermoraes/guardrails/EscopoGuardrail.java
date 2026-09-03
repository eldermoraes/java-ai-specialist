package com.eldermoraes.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Locale;
import org.jboss.logging.Logger;

/**
 * Segundo guardrail da fila: mantém o assistente dentro do assunto dele.
 *
 * A regra é uma lista de temas que não são de RH. É código puro: mesma pergunta, mesma
 * decisão, sempre — e sem gastar um token sequer, porque nada foi ao modelo ainda.
 *
 * O limite é honesto e faz parte do que se ensina aqui: esta regra só pega o que está na
 * lista. Quem escrever a mesma pergunta com outras palavras passa direto. Cobrir o que não
 * foi previsto exige um classificador, que é outro assunto — e outro custo.
 */
@ApplicationScoped
public class EscopoGuardrail implements InputGuardrail {

    private static final Logger LOG = Logger.getLogger(EscopoGuardrail.class);

    /** Temas que o assistente de RH não responde. */
    private static final List<String> TEMAS_FORA_DE_ESCOPO = List.of(
            "eleição", "eleicao", "política", "politica", "presidente", "candidato",
            "remédio", "remedio", "sintoma", "diagnóstico", "diagnostico", "dor de",
            "investimento", "criptomoeda", "bitcoin", "ação da bolsa"
    );

    @Override
    public InputGuardrailResult validate(UserMessage mensagemDoUsuario) {
        String texto = mensagemDoUsuario.singleText().toLowerCase(Locale.ROOT);

        for (String tema : TEMAS_FORA_DE_ESCOPO) {
            if (texto.contains(tema)) {
                LOG.warnf("Escopo: FAILURE — termo fora de escopo encontrado: '%s'", tema);
                return failure("Esta pergunta está fora do escopo do assistente de RH.");
            }
        }

        LOG.info("Escopo: OK — assunto dentro do escopo de RH");
        return success();
    }
}
