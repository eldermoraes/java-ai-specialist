package com.eldermoraes.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Primeiro da fila: procura frases de ataque conhecidas.
 *
 * É regra de código. Mesmo texto, mesma decisão, sempre, sem token e em microssegundos.
 * Por isso vem antes do classificador: o ataque que já está na lista nunca custa uma
 * chamada ao modelo pequeno.
 *
 * O limite é o de toda lista: ela só pega o que já foi visto. A mesma intenção em outras
 * palavras, em outra língua ou disfarçada passa direto. É o classificador, depois daqui,
 * que cobre esse resto.
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
            @ConfigProperty(name = "support-inbox.filtro.padroes") List<String> padroes) {
        this.padroes = padroes.stream().map(FiltroPorPadroesConhecidosGuardrail::normalizar).toList();
    }

    @Override
    public InputGuardrailResult validate(UserMessage mensagemDoUsuario) {
        String texto = normalizar(mensagemDoUsuario.singleText());

        for (String padrao : padroes) {
            if (texto.contains(padrao)) {
                LOG.warnf("Filtro: FATAL, padrão conhecido: '%s'. O classificador não é chamado", padrao);
                return fatal("A mensagem contém uma frase de ataque conhecida.");
            }
        }

        LOG.infof("Filtro: OK, nenhum dos %d padrões conhecidos. Segue para o classificador", padroes.size());
        return success();
    }

    /**
     * Minúsculas, sem acento e com um espaço só entre as palavras. Sem isso, "Ignore TODAS
     * as orientacoes" escaparia de "ignore todas as orientações" por um detalhe de digitação.
     */
    static String normalizar(String texto) {
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
