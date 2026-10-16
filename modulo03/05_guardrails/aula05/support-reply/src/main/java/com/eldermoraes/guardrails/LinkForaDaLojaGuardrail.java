package com.eldermoraes.guardrails;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Segundo e último da fila de saída: a regra de link.
 *
 * Link que não seja do domínio da loja (nem de um subdomínio dele) é trocado por um
 * marcador. É regra de código: sem token, em microssegundos, e sempre a mesma decisão.
 * Reprovar aqui seria pagar uma resposta inteira por um problema que uma expressão
 * regular resolve.
 *
 * Vem depois do juiz de propósito. O framework não deixa pedir reprompt depois que um
 * guardrail reescreveu a resposta; se esta regra viesse antes e reescrevesse, o juiz
 * não poderia reprovar aquela resposta. A regra que barra vem antes do juiz; a regra
 * que reescreve vem depois.
 *
 * Por ser o último da fila, devolve sempre a resposta que aprovou, reescrita ou não: a
 * aplicação recebe o texto do último resultado.
 */
@ApplicationScoped
public class LinkForaDaLojaGuardrail implements OutputGuardrail {

    private static final Logger LOG = Logger.getLogger(LinkForaDaLojaGuardrail.class);

    static final String MARCADOR = "[link removido]";

    /** http ou https, até o primeiro espaço ou fechamento de parêntese, colchete ou aspas. */
    private static final Pattern LINK = Pattern.compile("https?://[^\\s)\\]>\"']+");

    private static final String PONTUACAO_FINAL = ".,;:!?";

    private final String dominioDaLoja;

    @Inject
    public LinkForaDaLojaGuardrail(
            @ConfigProperty(name = "support-reply.loja.dominio") String dominioDaLoja) {
        this.dominioDaLoja = dominioDaLoja.toLowerCase(Locale.ROOT);
    }

    @Override
    public OutputGuardrailResult validate(AiMessage resposta) {
        String texto = resposta.text();
        Matcher links = LINK.matcher(texto);
        StringBuilder reescrito = new StringBuilder();
        int removidos = 0;

        while (links.find()) {
            String link = links.group();
            // Pontuação no fim ("...no site https://loja.com.br.") é da frase, não do link.
            String pontuacao = "";
            while (!link.isEmpty() && PONTUACAO_FINAL.indexOf(link.charAt(link.length() - 1)) >= 0) {
                pontuacao = link.charAt(link.length() - 1) + pontuacao;
                link = link.substring(0, link.length() - 1);
            }
            String troca = ehDaLoja(link) ? link : MARCADOR;
            if (!troca.equals(link)) {
                removidos++;
            }
            links.appendReplacement(reescrito, Matcher.quoteReplacement(troca + pontuacao));
        }
        links.appendTail(reescrito);

        if (removidos > 0) {
            LOG.infof("Regra de link: SUCCESS_WITH_RESULT, %d link(s) fora de %s reescrito(s). A resposta segue",
                    removidos, dominioDaLoja);
        } else {
            LOG.info("Regra de link: SUCCESS_WITH_RESULT, nenhum link de fora. A resposta segue inteira");
        }

        return successWith(reescrito.toString());
    }

    /** O domínio da loja e os subdomínios dele; qualquer outro host é "de fora". */
    private boolean ehDaLoja(String link) {
        try {
            String host = URI.create(link).getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.equals(dominioDaLoja) || host.endsWith("." + dominioDaLoja);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
