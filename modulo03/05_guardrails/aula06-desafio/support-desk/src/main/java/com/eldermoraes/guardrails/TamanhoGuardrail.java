package com.eldermoraes.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Primeiro da fila de entrada: o mais barato de todos, uma contagem de caracteres.
 *
 * Mensagem vazia e/ou longa demais dá fatal: a fila para aqui, sem chamar os guardrails
 * seguintes nem gastar uma chamada ao classificador.
 */
@ApplicationScoped
public class TamanhoGuardrail implements InputGuardrail {

    private static final Logger LOG = Logger.getLogger(TamanhoGuardrail.class);

    /** Teto de caracteres da mensagem. Quem define o limite é a aplicação, não o modelo. */
    static final int MAXIMO_DE_CARACTERES = 2_000;

    @Override
    public InputGuardrailResult validate(UserMessage mensagemDoUsuario) {
        String texto = mensagemDoUsuario.singleText();

        if (texto == null || texto.isBlank()) {
            LOG.warn("Tamanho: FATAL, mensagem vazia. A fila de entrada para aqui");
            return fatal("Mensagem vazia.");
        }

        if (texto.length() > MAXIMO_DE_CARACTERES) {
            LOG.warnf("Tamanho: FATAL, %d caracteres, acima do limite de %d. A fila de entrada para aqui",
                    texto.length(), MAXIMO_DE_CARACTERES);
            return fatal("Mensagem longa demais: " + texto.length()
                    + " caracteres, o limite é " + MAXIMO_DE_CARACTERES + ".");
        }

        LOG.infof("Tamanho: OK, %d caracteres. Segue para o escopo", texto.length());
        return success();
    }
}
