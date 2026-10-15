package com.eldermoraes.guardrails;

import com.eldermoraes.ai.ClassificadorDeEscopo;
import com.eldermoraes.ai.ClassificadorDeEscopo.Veredito;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Quarto e último da fila: o único guardrail não determinístico do projeto.
 *
 * Os três anteriores são regra de código. Este consulta um modelo, e por isso é diferente
 * em tudo o que importa:
 *
 * - gasta token: o token do guardrail, não o da pergunta, e antes dela existir;
 * - soma a latência de uma chamada a mais;
 * - erra. Pode barrar quem tinha o direito de perguntar e pode deixar passar quem não
 *   tinha. Nenhum dos três anteriores faz isso.
 *
 * Então por que ele existe? Porque o EscopoGuardrail só enxerga o que está na lista dele.
 * "Me ensina a fazer um bolo?" não tem nenhum termo listado e passa direto. Este aqui
 * entende a pergunta e recusa.
 *
 * Vem por último de propósito: o que a regra de código já resolveu não chega até aqui, e
 * o que o guardrail anterior mascarou chega mascarado: o CPF não vai nem para o
 * classificador.
 */
@ApplicationScoped
public class EscopoPorSentidoGuardrail implements InputGuardrail {

    private static final Logger LOG = Logger.getLogger(EscopoPorSentidoGuardrail.class);

    private final ClassificadorDeEscopo classificador;

    @Inject
    public EscopoPorSentidoGuardrail(ClassificadorDeEscopo classificador) {
        this.classificador = classificador;
    }

    @Override
    public InputGuardrailResult validate(UserMessage mensagemDoUsuario) {
        String texto = mensagemDoUsuario.singleText();
        Veredito veredito = classificador.classificar(texto);

        if (veredito == Veredito.FORA) {
            LOG.warn("Escopo por sentido: FATAL, o classificador considerou a pergunta fora de RH");
            return fatal("Esta pergunta está fora do escopo do assistente de RH.");
        }

        LOG.info("Escopo por sentido: OK, o classificador considerou a pergunta dentro de RH");
        return success();
    }
}
