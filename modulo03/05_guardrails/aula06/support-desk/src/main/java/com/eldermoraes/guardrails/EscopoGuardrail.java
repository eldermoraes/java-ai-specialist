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
 * Segundo da fila de entrada: mantém o assistente dentro do assunto dele.
 *
 * A regra é uma lista de palavras de assuntos que não são da loja. É código puro: mesma
 * mensagem, mesma decisão, sempre, sem gastar um token, porque nada foi ao modelo ainda.
 *
 * O limite é o de toda lista: ela só pega o que está nela. Assunto de fora escrito com
 * outras palavras passa, e quem recusa é o próprio modelo principal, seguindo o system
 * message. Um classificador de escopo alcançaria esse resto; ficou de fora deste projeto
 * porque seria a segunda chamada ao modelo pequeno antes de qualquer resposta (ver README).
 *
 * Desvio de escopo não é injection: esta lista procura assunto; o classificador, no fim
 * da fila, procura intenção de mudar o comportamento do assistente.
 *
 * A lista vem de application.properties, e a comparação ignora maiúsculas, acentos e
 * espaços repetidos, como no filtro.
 */
@ApplicationScoped
public class EscopoGuardrail implements InputGuardrail {

    private static final Logger LOG = Logger.getLogger(EscopoGuardrail.class);

    private final List<String> palavrasDeFora;

    @Inject
    public EscopoGuardrail(@ConfigProperty(name = "support-desk.escopo.palavras") List<String> palavrasDeFora) {
        this.palavrasDeFora = palavrasDeFora.stream().map(Normalizacao::normalizar).toList();
    }

    @Override
    public InputGuardrailResult validate(UserMessage mensagemDoUsuario) {
        String texto = Normalizacao.normalizar(mensagemDoUsuario.singleText());

        for (String palavra : palavrasDeFora) {
            if (texto.contains(palavra)) {
                LOG.warnf("Escopo: FATAL, palavra de fora do assunto da loja: '%s'. A fila de entrada para aqui", palavra);
                return fatal("A mensagem está fora do assunto do assistente de suporte.");
            }
        }

        LOG.infof("Escopo: OK, nenhuma das %d palavras de fora. Segue para o filtro", palavrasDeFora.size());
        return success();
    }
}
