package com.eldermoraes.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * Quarto da fila de entrada: impede que dado sensível saia da aplicação dentro do prompt.
 *
 * Duas reações para dois tipos de dado, e a escolha entre elas é de negócio:
 *
 * - chave de API dá fatal: esse dado não pode sair em nenhuma hipótese, e a mensagem não
 *   serve para nada sem ele, então a requisição para aqui;
 * - CPF, o código mascara e segue: o assistente não precisa do número para responder
 *   nem para triar, então a mensagem segue reescrita, sem o dado.
 *
 * Vem antes do classificador de propósito: quem roda depois de uma reescrita já recebe a
 * versão reescrita, e assim o CPF não chega nem ao modelo pequeno.
 *
 * Os dois casos são detectados por expressão regular, que resolve bem o que tem formato
 * fixo. Dado sensível sem formato (um endereço escrito por extenso) não é alcançado assim.
 */
@ApplicationScoped
public class DadoSensivelGuardrail implements InputGuardrail {

    private static final Logger LOG = Logger.getLogger(DadoSensivelGuardrail.class);

    static final String MARCADOR_DE_CPF = "[CPF]";

    /** CPF com ou sem pontuação: 000.000.000-00 ou 00000000000. */
    private static final Pattern CPF = Pattern.compile(
            "\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");

    /** Chave de API no formato usado pelos provedores mais comuns. */
    private static final Pattern CHAVE_DE_API = Pattern.compile(
            "\\b(sk|api|key)[-_][A-Za-z0-9]{16,}\\b");

    @Override
    public InputGuardrailResult validate(UserMessage mensagemDoUsuario) {
        String texto = mensagemDoUsuario.singleText();

        if (CHAVE_DE_API.matcher(texto).find()) {
            LOG.warn("Dado sensível: FATAL, chave de API na mensagem. Nenhum modelo é chamado");
            return fatal("A mensagem contém uma chave de API e não foi enviada ao modelo.");
        }

        if (CPF.matcher(texto).find()) {
            LOG.info("Dado sensível: SUCCESS_WITH_RESULT, CPF mascarado. Segue mascarada para o classificador");
            return successWith(mascararCpf(texto));
        }

        LOG.info("Dado sensível: OK, nada encontrado. Segue para o classificador");
        return success();
    }

    /** A mesma máscara, para quem mais precisar dela (o juiz recebe a pergunta por aqui). */
    public static String mascararCpf(String texto) {
        return CPF.matcher(texto).replaceAll(java.util.regex.Matcher.quoteReplacement(MARCADOR_DE_CPF));
    }
}
