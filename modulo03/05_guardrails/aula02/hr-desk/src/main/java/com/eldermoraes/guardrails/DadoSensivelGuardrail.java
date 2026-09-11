package com.eldermoraes.guardrails;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * Terceiro guardrail da fila: impede que dado sensível saia da empresa dentro do prompt.
 *
 * Ele mostra as duas reações possíveis diante do mesmo tipo de risco, e a escolha entre
 * elas é de negócio, não de framework:
 *
 * - chave de API é bloqueada: esse dado não pode sair em nenhuma hipótese, e a pergunta
 *   não faz sentido sem ele, então a requisição para aqui;
 * - CPF é mascarado: o modelo não precisa do número para responder sobre férias ou folha,
 *   então a mensagem segue reescrita, sem o dado.
 *
 * Quando um guardrail reescreve a mensagem, quem roda depois dele já recebe a versão
 * reescrita. Nesta aula ele é o último da fila, mas vale lembrar disso ao mudar a ordem.
 *
 * Os dois casos são detectados por expressão regular, que resolve bem o que tem formato
 * fixo. Dado sensível sem formato (um endereço escrito por extenso, por exemplo) não é
 * alcançado assim.
 */
@ApplicationScoped
public class DadoSensivelGuardrail implements InputGuardrail {

    private static final Logger LOG = Logger.getLogger(DadoSensivelGuardrail.class);

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
            LOG.warn("Dado sensível: FAILURE, chave de API na pergunta. Requisição bloqueada");
            return failure("A pergunta contém uma chave de API e não foi enviada ao modelo.");
        }

        if (CPF.matcher(texto).find()) {
            String mascarado = CPF.matcher(texto).replaceAll("[CPF]");
            LOG.info("Dado sensível: SUCCESS_WITH_RESULT, CPF mascarado. A pergunta segue");
            return successWith(mascarado);
        }

        LOG.info("Dado sensível: OK, nada encontrado");
        return success();
    }
}
