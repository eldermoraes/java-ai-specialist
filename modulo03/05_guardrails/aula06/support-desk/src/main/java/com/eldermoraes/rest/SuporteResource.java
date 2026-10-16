package com.eldermoraes.rest;

import com.eldermoraes.ai.AssistenteDeSuporte;
import com.eldermoraes.guardrails.ContadorDeChamadas;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailException;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * Duas portas síncronas, texto puro na entrada e na saída. Sem streaming, como em todo o
 * submódulo: o juiz e o guardrail de formato só avaliam a resposta inteira, e com
 * streaming o cliente leria o texto antes do veredito.
 *
 * - POST /suporte: o cliente pergunta e recebe a resposta em texto.
 * - POST /suporte/email: o atendente cola o e-mail de um cliente e recebe a triagem em
 *   JSON.
 *
 * O que quem chama lê em cada desfecho é decisão da aplicação, e está aqui:
 *
 * - fatal na entrada: 400 com uma recusa curta, sem dizer qual camada barrou nem por
 *   quê. Explicar ao atacante o que o filtro procura é ensiná-lo a escrever a próxima
 *   frase;
 * - tentativas de saída esgotadas: 200 com uma mensagem fixa, o fallback. Quem lê é
 *   uma pessoa (o cliente ou o atendente), e a mensagem fixa é a resposta dela; o motivo
 *   da reprovação vai para o log, e a resposta reprovada nunca entra no corpo.
 *
 * No fim de toda requisição sai uma linha de resumo com o desfecho e a conta de chamadas a
 * modelo. É a linha que diz quanto a fila custou.
 */
@Path("/suporte")
public class SuporteResource {

    private static final Logger LOG = Logger.getLogger(SuporteResource.class);

    static final String RECUSA = "Não posso processar essa mensagem.";

    /** O fallback do cliente: nenhuma tentativa foi aprovada pelo juiz. */
    static final String FALLBACK_DO_CLIENTE = "Não consegui confirmar uma resposta para o seu caso agora. "
            + "Encaminhei a sua conversa para um atendente, que responde por aqui em breve.";

    /** O fallback do atendente: nenhuma tentativa de triagem passou no formato. */
    static final String FALLBACK_DA_TRIAGEM = "Não consegui fazer a triagem deste e-mail agora. "
            + "Ele foi encaminhado para triagem manual.";

    private static final Pattern NOME_DO_GUARDRAIL = Pattern.compile("([A-Za-z]+Guardrail)");

    @Inject
    AssistenteDeSuporte assistente;

    @Inject
    ContadorDeChamadas contador;

    @POST
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Response perguntar(String pergunta) {
        return atender("/suporte", () -> assistente.responder(pergunta), FALLBACK_DO_CLIENTE);
    }

    @POST
    @Path("/email")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    public Response triarEmail(String email) {
        return atender("/suporte/email", () -> assistente.triarEmail(email), FALLBACK_DA_TRIAGEM);
    }

    private Response atender(String porta, Supplier<String> chamada, String fallback) {
        try {
            String resposta = chamada.get();
            resumo(porta, "passou pela fila inteira");
            return Response.ok(resposta, MediaType.TEXT_PLAIN).build();
        } catch (InputGuardrailException e) {
            // Um guardrail de entrada deu fatal: o modelo principal não foi chamado.
            resumo(porta, "recusada na entrada por " + camadaQueDecidiu(e.getMessage()));
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN)
                    .entity(RECUSA)
                    .build();
        } catch (OutputGuardrailException e) {
            // As tentativas acabaram sem uma resposta aprovada. A mensagem da exceção traz
            // o motivo de cada reprovação: serve para o log, não para quem chamou.
            LOG.warnf("Tentativas esgotadas, fallback: %s", e.getMessage());
            resumo(porta, "fallback, tentativas de saída esgotadas");
            return Response.ok(fallback, MediaType.TEXT_PLAIN).build();
        }
    }

    private void resumo(String porta, String desfecho) {
        LOG.infof("Resumo da requisição %s: %s, %d chamada(s) a modelo %s",
                porta, desfecho, contador.total(), contador.modelos());
    }

    /** O nome da classe do guardrail que deu fatal, tirado da mensagem da exceção. */
    static String camadaQueDecidiu(String mensagem) {
        Matcher nome = NOME_DO_GUARDRAIL.matcher(String.valueOf(mensagem));
        return nome.find() ? nome.group(1) : "um guardrail de entrada";
    }
}
