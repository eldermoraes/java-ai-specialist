package com.eldermoraes.guardrails;

import com.eldermoraes.ai.ClassificadorDeInjection;
import com.eldermoraes.ai.ClassificadorDeInjection.Veredito;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Quinto e último da fila de entrada: o classificador de injection, o único que consulta
 * um modelo.
 *
 * Só chega aqui o que as quatro regras de código deixaram passar, já com o CPF mascarado.
 * O filtro procura frases; este guardrail pergunta a um modelo pequeno se o texto tenta
 * mudar o comportamento do assistente. É o que alcança a paráfrase, a outra língua e a
 * instrução escondida no meio de um e-mail.
 *
 * O preço está no log: uma chamada ao modelo pequeno em toda mensagem que chega até aqui,
 * inclusive nas legítimas. E o veredito pode errar para os dois lados: barrar um cliente
 * de verdade (falso positivo) e deixar passar um ataque (falso negativo).
 *
 * Por ser o último da fila, devolve sempre o texto que aprovou (successWith): a mensagem
 * que vai ao modelo principal é a do último resultado, e um success() puro aqui
 * descartaria a máscara que o dado sensível aplicou. FilaDeEntradaTest fixa isso.
 */
@ApplicationScoped
public class ClassificadorDeInjectionGuardrail implements InputGuardrail {

    private static final Logger LOG = Logger.getLogger(ClassificadorDeInjectionGuardrail.class);

    private final ClassificadorDeInjection classificador;

    @Inject
    public ClassificadorDeInjectionGuardrail(ClassificadorDeInjection classificador) {
        this.classificador = classificador;
    }

    @Override
    public InputGuardrailResult validate(UserMessage mensagemDoUsuario) {
        String texto = mensagemDoUsuario.singleText();
        Veredito veredito = classificador.classificar(texto);

        if (veredito == Veredito.INJECTION) {
            LOG.warn("Classificador: FATAL, intenção de mudar o comportamento do assistente. O modelo principal não é chamado");
            return fatal("O classificador identificou uma tentativa de prompt injection.");
        }

        LOG.info("Classificador: OK, nenhuma tentativa de mudar o comportamento. Segue para o modelo principal");
        return successWith(texto);
    }
}
