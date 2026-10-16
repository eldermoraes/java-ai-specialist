package com.eldermoraes.guardrails;

import static com.eldermoraes.guardrails.Requisicoes.DOMINIO_DA_LOJA;
import static com.eldermoraes.guardrails.Requisicoes.PRAZO_DE_TROCA;
import static com.eldermoraes.guardrails.Requisicoes.PRESSAO_POR_EXCECAO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eldermoraes.ai.AssistenteDeSuporte;
import com.eldermoraes.ai.JuizDePolitica;
import com.eldermoraes.dto.Veredito;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailExecutor;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkus.test.junit.QuarkusTest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O teste anterior cobre a decisão de cada guardrail. Este cobre o que acontece depois
 * dela: quem refaz a chamada ao assistente, o que vai junto, quando as tentativas acabam
 * e em que ordem a fila roda.
 *
 * A fila é a declarada no AI Service e o laço de tentativas é o do framework. No lugar
 * do assistente entra um modelo de roteiro (respostas combinadas, que anota as mensagens
 * de cada chamada); no lugar do juiz, um dublê que aprova a resposta que está numa lista
 * e reprova as outras, contando o que viu. Nenhum prompt sai daqui: o teste roda sem
 * Ollama, e o resultado é sempre o mesmo.
 *
 * O Quarkus sobe porque a extensão substitui a configuração de guardrails por uma que lê
 * o application.properties, e essa configuração só existe com a aplicação de pé. O limite
 * de cada teste é declarado no próprio teste, para não depender do valor configurado.
 *
 * Prova as afirmações do README que o log mostra na demo:
 *
 * - juiz aprova: a resposta sai sem nova chamada;
 * - juiz reprova: nova chamada, e a última mensagem dela carrega o motivo do juiz;
 * - esgotou: exceção, e a resposta reprovada não é devolvida;
 * - a regra de link roda sobre a resposta que o juiz aprovou, e só depois dele;
 * - na ordem inversa, uma reescrita antes do juiz bloquearia o reprompt.
 */
@QuarkusTest
class FilaDeSaidaTest {

    private static final String RESPOSTA_APROVADA =
            "Você tem até 30 dias corridos após a entrega para trocar um produto com defeito.";

    private static final String RESPOSTA_QUE_CONCEDE =
            "Como você é cliente antigo, vou abrir uma exceção e autorizar a troca.";

    private static final String RESPOSTA_QUE_RECUSA =
            "Sinto muito, mas o prazo de troca é de 7 dias corridos e não abrimos exceção.";

    private static final String RESPOSTA_COM_LINK_QUE_CONCEDE =
            "Abri uma exceção para você. Veja em https://rastreio.correios.com.br/123";

    private static final String RESPOSTA_COM_LINK_QUE_RECUSA =
            "O prazo de 7 dias já passou e não abrimos exceção. "
                    + "Regras em https://lojaexemplo.com.br/trocas e dúvidas em https://ajuda.exemplo.org";

    private static final String RESPOSTA_COM_LINK_REESCRITA =
            "O prazo de 7 dias já passou e não abrimos exceção. "
                    + "Regras em https://lojaexemplo.com.br/trocas e dúvidas em [link removido]";

    private static final String MOTIVO =
            "A resposta concede exceção de prazo, que a política proíbe.";

    /** O dublê do juiz: aprova só as respostas desta lista, e anota cada julgamento. */
    private final List<String> respostasQueOJuizAprova = List.of(
            RESPOSTA_APROVADA, RESPOSTA_QUE_RECUSA, RESPOSTA_COM_LINK_QUE_RECUSA);
    private final List<String> julgamentos = new ArrayList<>();

    private final JuizDePolitica juiz = (pergunta, resposta) -> {
        julgamentos.add(resposta);
        return respostasQueOJuizAprova.contains(resposta)
                ? Veredito.aprovar()
                : Veredito.reprovar(MOTIVO);
    };

    @Test
    @DisplayName("a fila declarada é o juiz e depois a regra de link")
    void filaDeclaradaNoAiService() throws Exception {
        assertEquals(
                List.of(JuizDePoliticaGuardrail.class, LinkForaDaLojaGuardrail.class),
                List.of(declaradaNoAiService()));
    }

    @Test
    @DisplayName("requisição 1: o juiz aprova e a resposta sai numa chamada só")
    void juizAprovaNaPrimeiraTentativa() throws Exception {
        var modelo = new ModeloDeRoteiro();
        var request = requisicao(PRAZO_DE_TROCA, RESPOSTA_APROVADA, modelo);

        var resultado = executor(2, filaDoProjeto()).execute(request);

        assertEquals(1, modelo.chamadasAoModelo());
        assertEquals(List.of(RESPOSTA_APROVADA), julgamentos);
        assertEquals(RESPOSTA_APROVADA, textoDevolvido(resultado, request));
    }

    @Test
    @DisplayName("requisição 3: o juiz reprova, a segunda chamada leva o motivo e é aprovada")
    void repromptLevaOMotivoDoJuiz() throws Exception {
        var modelo = new ModeloDeRoteiro(RESPOSTA_QUE_RECUSA);
        var request = requisicao(PRESSAO_POR_EXCECAO, RESPOSTA_QUE_CONCEDE, modelo);

        var resultado = executor(2, filaDoProjeto()).execute(request);

        assertEquals(2, modelo.chamadasAoModelo());
        assertTrue(ultimaMensagemDaSegundaChamada(modelo).contains(MOTIVO));
        assertEquals(List.of(RESPOSTA_QUE_CONCEDE, RESPOSTA_QUE_RECUSA), julgamentos);
        assertEquals(RESPOSTA_QUE_RECUSA, textoDevolvido(resultado, request));
    }

    @Test
    @DisplayName("requisição 5: o juiz reprova até esgotar, e a resposta reprovada não é devolvida")
    void limiteEsgotadoViraExcecao() throws Exception {
        var modelo = new ModeloDeRoteiro(RESPOSTA_QUE_CONCEDE);
        var request = requisicao(PRESSAO_POR_EXCECAO, RESPOSTA_QUE_CONCEDE, modelo);
        var executor = executor(2, filaDoProjeto());

        var erro = assertThrows(OutputGuardrailException.class, () -> executor.execute(request));

        assertEquals(2, modelo.chamadasAoModelo());
        assertEquals(2, julgamentos.size());
        assertTrue(erro.getMessage().contains(MOTIVO));
        assertFalse(erro.getMessage().contains(RESPOSTA_QUE_CONCEDE));
    }

    @Test
    @DisplayName("a regra de link reescreve só a resposta que o juiz aprovou, e o juiz vê o texto original")
    void reescritaDeLinkSoDepoisDoJuiz() throws Exception {
        var modelo = new ModeloDeRoteiro(RESPOSTA_COM_LINK_QUE_RECUSA);
        var request = requisicao(PRESSAO_POR_EXCECAO, RESPOSTA_COM_LINK_QUE_CONCEDE, modelo);

        var resultado = executor(2, filaDoProjeto()).execute(request);

        assertEquals(2, modelo.chamadasAoModelo());
        assertEquals(List.of(RESPOSTA_COM_LINK_QUE_CONCEDE, RESPOSTA_COM_LINK_QUE_RECUSA), julgamentos);
        assertEquals(RESPOSTA_COM_LINK_REESCRITA, textoDevolvido(resultado, request));
    }

    @Test
    @DisplayName("por que o juiz vem antes: na ordem inversa, a reescrita bloqueia o reprompt")
    void reescritaAntesDoJuizBloquearia() throws Exception {
        var modelo = new ModeloDeRoteiro(RESPOSTA_QUE_RECUSA);
        var request = requisicao(PRESSAO_POR_EXCECAO, RESPOSTA_COM_LINK_QUE_CONCEDE, modelo);
        var filaInvertida = List.of(regraDeLink(), juiz());
        var executor = executor(2, filaInvertida);

        var erro = assertThrows(OutputGuardrailException.class, () -> executor.execute(request));

        // A regra reescreveu o link, o juiz reprovou, e o framework recusou o reprompt:
        // nenhuma segunda chamada, mesmo com limite para ela.
        assertEquals(1, modelo.chamadasAoModelo());
        assertTrue(erro.getMessage().contains("not allowed after a rewritten output"));
    }

    // ── a fila ──────────────────────────────────────────────────────────────

    private static Class<? extends OutputGuardrail>[] declaradaNoAiService() throws Exception {
        return AssistenteDeSuporte.class.getMethod("responder", String.class)
                .getAnnotation(OutputGuardrails.class)
                .value();
    }

    /** A fila que o AI Service declara, na ordem em que está declarada, com os dublês. */
    private List<OutputGuardrail> filaDoProjeto() throws Exception {
        Map<Class<?>, OutputGuardrail> instancias = Map.of(
                JuizDePoliticaGuardrail.class, juiz(),
                LinkForaDaLojaGuardrail.class, regraDeLink());
        var fila = new ArrayList<OutputGuardrail>();
        for (var tipo : declaradaNoAiService()) {
            fila.add(instancias.get(tipo));
        }
        return fila;
    }

    private JuizDePoliticaGuardrail juiz() {
        return new JuizDePoliticaGuardrail(juiz);
    }

    private static LinkForaDaLojaGuardrail regraDeLink() {
        return new LinkForaDaLojaGuardrail(DOMINIO_DA_LOJA);
    }

    /**
     * O laço de tentativas do framework, com o limite declarado pelo teste. O limite conta
     * chamadas ao modelo: 1 é só a chamada original, 2 é a original mais uma tentativa.
     */
    private static OutputGuardrailExecutor executor(int limiteDeChamadas, List<OutputGuardrail> fila) {
        OutputGuardrailsConfig limite = () -> limiteDeChamadas;
        return OutputGuardrailExecutor.builder()
                .config(limite)
                .guardrails(fila)
                .build();
    }

    private static OutputGuardrailRequest requisicao(String pergunta, String primeiraResposta, ChatExecutor modelo) {
        return OutputGuardrailRequest.builder()
                .responseFromLLM(ChatResponse.builder()
                        .aiMessage(AiMessage.from(primeiraResposta))
                        .build())
                .chatExecutor(modelo)
                .requestParams(GuardrailRequestParams.builder()
                        .userMessageTemplate("{pergunta}")
                        .variables(Map.of("pergunta", pergunta))
                        .invocationContext(InvocationContext.builder()
                                .interfaceName(AssistenteDeSuporte.class.getName())
                                .methodName("responder")
                                .build())
                        .build())
                .build();
    }

    private static String textoDevolvido(
            dev.langchain4j.guardrail.OutputGuardrailResult resultado, OutputGuardrailRequest request) {
        return resultado.<ChatResponse>response(request).aiMessage().text();
    }

    private static String ultimaMensagemDaSegundaChamada(ModeloDeRoteiro modelo) {
        var mensagens = modelo.mensagensDaChamada(1);
        return ((UserMessage) mensagens.getLast()).singleText();
    }

    /**
     * Entra no lugar do assistente: devolve as respostas combinadas, na ordem, e guarda as
     * mensagens que recebeu em cada chamada.
     */
    private static final class ModeloDeRoteiro implements ChatExecutor {

        private final Deque<String> roteiro;
        private final List<List<ChatMessage>> chamadas = new ArrayList<>();

        ModeloDeRoteiro(String... respostas) {
            this.roteiro = new ArrayDeque<>(List.of(respostas));
        }

        @Override
        public ChatResponse execute() {
            return execute(List.of());
        }

        @Override
        public ChatResponse execute(List<ChatMessage> mensagens) {
            chamadas.add(List.copyOf(mensagens));
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(roteiro.poll()))
                    .build();
        }

        /** A chamada original mais as tentativas que este executor atendeu. */
        int chamadasAoModelo() {
            return 1 + chamadas.size();
        }

        List<ChatMessage> mensagensDaChamada(int indice) {
            return chamadas.get(indice - 1);
        }
    }
}
