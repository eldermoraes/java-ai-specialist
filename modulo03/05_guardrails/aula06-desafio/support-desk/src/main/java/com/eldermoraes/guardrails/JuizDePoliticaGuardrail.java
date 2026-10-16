package com.eldermoraes.guardrails;

import com.eldermoraes.ai.JuizDePolitica;
import com.eldermoraes.dto.Veredito;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Primeiro da fila de saída da resposta ao cliente: o juiz.
 *
 * Entrega ao juiz a pergunta do cliente e a resposta do assistente, e lê o veredito.
 * Aprovada, a resposta segue para a regra de link. Reprovada, o guardrail pede reprompt
 * e o motivo do juiz vira a mensagem que entra na conversa: o assistente responde de
 * novo sabendo o que errou, e a resposta nova passa pela fila de saída inteira outra vez.
 *
 * A pergunta vem das variáveis do template do AI Service ({pergunta}), que guardam o
 * texto como o cliente digitou, antes da fila de entrada. Por isso ela passa pela mesma
 * máscara de CPF antes de ir ao juiz: o que a entrada escondeu do modelo grande não
 * chega ao modelo pequeno por esta porta.
 *
 * Vem antes da regra de link porque, depois de uma reescrita, o framework não deixa o
 * guardrail seguinte pedir reprompt.
 *
 * O preço está no log: uma chamada ao modelo pequeno em toda resposta, e mais uma em
 * cada tentativa de reprompt. E o veredito pode errar para os dois lados.
 */
@ApplicationScoped
public class JuizDePoliticaGuardrail implements OutputGuardrail {

    private static final Logger LOG = Logger.getLogger(JuizDePoliticaGuardrail.class);

    private final JuizDePolitica juiz;

    @Inject
    public JuizDePoliticaGuardrail(JuizDePolitica juiz) {
        this.juiz = juiz;
    }

    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        String resposta = request.responseFromLLM().aiMessage().text();
        String pergunta = DadoSensivelGuardrail.mascararCpf(String.valueOf(
                request.requestParams().variables().getOrDefault("pergunta", "")));

        Veredito veredito = juiz.julgar(pergunta, resposta);

        if (veredito.aprovada()) {
            LOG.info("Juiz: OK, a resposta respeita a política. Segue para a regra de link");
            return success();
        }

        LOG.warnf("Juiz: REPROMPT, %s", veredito.motivo());
        return reprompt(
                "O juiz reprovou a resposta: " + veredito.motivo(),
                "Sua resposta foi reprovada pelo seguinte motivo: " + veredito.motivo()
                        + " Escreva outra resposta ao cliente respeitando a política de"
                        + " atendimento, sem prometer nem conceder o que ela não garante.");
    }
}
