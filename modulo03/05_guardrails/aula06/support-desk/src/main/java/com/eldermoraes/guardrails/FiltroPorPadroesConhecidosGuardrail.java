package com.eldermoraes.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Terceiro da fila de entrada: procura frases de ataque conhecidas.
 *
 * É regra de código. Mesmo texto, mesma decisão, sempre, sem token e em microssegundos.
 * Vem antes do dado sensível porque quem dá fatal de graça vem antes de quem reescreve:
 * uma frase de ataque não merece nem a expressão regular do CPF. E vem antes do
 * classificador porque o ataque que já está na lista nunca custa uma chamada ao modelo
 * pequeno.
 *
 * O limite é o de toda lista: ela só pega o que já foi visto. A mesma intenção em outras
 * palavras, em outra língua ou escondida num e-mail passa direto. É o classificador, no
 * fim da fila, que cobre esse resto.
 *
 * A lista vem de application.properties, e não de uma constante, de propósito: o que o
 * classificador barra e você confirma como ataque vira padrão novo aqui, sem recompilar.
 */
@ApplicationScoped
public class FiltroPorPadroesConhecidosGuardrail implements InputGuardrail {

    private static final Logger LOG = Logger.getLogger(FiltroPorPadroesConhecidosGuardrail.class);

    private final List<String> padroes;

    @Inject
    public FiltroPorPadroesConhecidosGuardrail(
            @ConfigProperty(name = "support-desk.filtro.padroes") List<String> padroes) {
        this.padroes = padroes.stream().map(Normalizacao::normalizar).toList();
    }

    @Override
    public InputGuardrailResult validate(UserMessage mensagemDoUsuario) {
        String texto = Normalizacao.normalizar(mensagemDoUsuario.singleText());

        for (String padrao : padroes) {
            if (texto.contains(padrao)) {
                LOG.warnf("Filtro: FATAL, padrão conhecido: '%s'. Nenhum modelo é chamado", padrao);
                return fatal("A mensagem contém uma frase de ataque conhecida.");
            }
        }

        LOG.infof("Filtro: OK, nenhum dos %d padrões conhecidos. Segue para o dado sensível", padroes.size());
        return success();
    }
}
