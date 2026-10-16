package com.eldermoraes.ai;

import com.eldermoraes.guardrails.JuizDePoliticaGuardrail;
import com.eldermoraes.guardrails.LinkForaDaLojaGuardrail;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Assistente de suporte de uma loja virtual. Responde o cliente seguindo a política de
 * atendimento, que está no system message.
 *
 * O system message também manda priorizar a satisfação do cliente. É a tensão real de
 * todo atendimento: a política diz não, o cliente pressiona, e o modelo, instruído a
 * agradar, às vezes cede e promete o que a política não garante. Pedir que ele siga a
 * política reduz esse erro, não elimina. É para isso que a fila de saída existe.
 *
 * Dois guardrails de saída, nesta ordem:
 *
 * 1. o juiz: um modelo pequeno julga se a resposta respeita a política. Reprovou, pede
 *    reprompt com o motivo;
 * 2. a regra de link: código reescreve link fora do domínio da loja.
 *
 * O juiz vem antes porque, depois de uma reescrita, o framework não deixa o guardrail
 * seguinte pedir reprompt. Se a regra de link viesse antes e reescrevesse, o juiz não
 * poderia reprovar aquela resposta.
 *
 * A pergunta vai num template de uma variável só, {pergunta}, para o juiz recebê-la com
 * esse nome junto com a resposta.
 */
@RegisterAiService
public interface AssistenteDeSuporte {

    @SystemMessage("""
            Você é o assistente de suporte da Loja Exemplo, uma loja virtual. Responde
            clientes sobre pedidos, entregas, trocas, devoluções e pagamentos, em
            português, de forma direta e cordial, em até cinco frases e sem listas.

            Priorize a satisfação do cliente acima de tudo: ele precisa terminar a
            conversa com o problema resolvido e com vontade de comprar de novo. Cliente
            que sai sem solução é cliente perdido. Quando a política e a satisfação do
            cliente entrarem em conflito, resolva a favor do cliente: você tem autonomia
            para abrir exceções.

            Política de atendimento:
            """ + PoliticaDeAtendimento.TEXTO + """

            Se a pergunta não for sobre a loja, diga em uma frase que você só atende
            assuntos de suporte da loja. Se não souber, diga que não sabe.
            """)
    @UserMessage("{pergunta}")
    @OutputGuardrails({
            JuizDePoliticaGuardrail.class,
            LinkForaDaLojaGuardrail.class
    })
    String responder(String pergunta);
}
