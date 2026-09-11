package com.eldermoraes.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Primeiro guardrail da fila: o mais barato de todos, uma contagem de caracteres.
 *
 * Repare que ele usa os dois tipos de reprovação, e a diferença entre elas é o assunto
 * da aula:
 *
 * - mensagem vazia devolve fatal: não há o que avaliar, então a fila para aqui e os
 *   guardrails seguintes nem chegam a rodar;
 * - mensagem longa demais devolve failure: reprova e registra o motivo, mas deixa os
 *   seguintes rodarem, para que todas as reprovações cheguem juntas à aplicação.
 */
@ApplicationScoped
public class TamanhoGuardrail implements InputGuardrail {

    private static final Logger LOG = Logger.getLogger(TamanhoGuardrail.class);

    /** Teto de caracteres da pergunta. Quem define o limite é a aplicação, não o modelo. */
    private static final int MAXIMO_DE_CARACTERES = 2_000;

    @Override
    public InputGuardrailResult validate(UserMessage mensagemDoUsuario) {
        String texto = mensagemDoUsuario.singleText();

        if (texto == null || texto.isBlank()) {
            LOG.warn("Tamanho: FATAL, pergunta vazia. A fila de guardrails para aqui");
            return fatal("Pergunta vazia.");
        }

        if (texto.length() > MAXIMO_DE_CARACTERES) {
            LOG.warnf("Tamanho: FAILURE, %d caracteres, acima do limite de %d",
                    texto.length(), MAXIMO_DE_CARACTERES);
            return failure("Pergunta longa demais: " + texto.length()
                    + " caracteres, o limite é " + MAXIMO_DE_CARACTERES + ".");
        }

        LOG.infof("Tamanho: OK, %d caracteres", texto.length());
        return success();
    }
}
