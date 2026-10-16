package com.eldermoraes.guardrails;

import static com.eldermoraes.guardrails.Requisicoes.DOMINIO_DA_LOJA;
import static com.eldermoraes.guardrails.Requisicoes.EMAIL_COM_CPF_MASCARADO;
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
import dev.langchain4j.guardrail.OutputGuardrailResult;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * O teste anterior cobre a decisão de cada guardrail. Este cobre o que acontece depois
 * dela, nas duas filas de saída: quem refaz a chamada ao modelo, o que vai junto, quando
 * as tentativas acabam e em que ordem a fila roda.
 *
 * A fila é a declarada no AI Service e o laço de tentativas é o do framework. No lugar do
 * modelo grande entra um modelo de roteiro (respostas combinadas, que anota as mensagens
 * de cada chamada); no lugar do juiz, um dublê que aprova as respostas de uma lista e
 * reprova as outras. Nenhum prompt sai daqui: o teste roda sem Ollama, e o resultado é
 * sempre o mesmo.
 *
 * O Quarkus sobe porque a extensão substitui a configuração de guardrails por uma que lê
 * o application.properties, e essa configuração só existe com a aplicação de pé. O limite
 * de cada teste é declarado no próprio teste, para não depender do valor configurado.
 */
@QuarkusTest
class FilaDeSaidaTest {

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

    private static OutputGuardrailRequest requisicao(
            String metodo, String variavel, String texto, String primeiraResposta, ChatExecutor modelo) {
        return OutputGuardrailRequest.builder()
                .responseFromLLM(ChatResponse.builder()
                        .aiMessage(AiMessage.from(primeiraResposta))
                        .build())
                .chatExecutor(modelo)
                .requestParams(GuardrailRequestParams.builder()
                        .userMessageTemplate("{" + variavel + "}")
                        .variables(Map.of(variavel, texto))
                        .invocationContext(InvocationContext.builder()
                                .interfaceName(AssistenteDeSuporte.class.getName())
                                .methodName(metodo)
                                .build())
                        .build())
                .build();
    }

    private static Class<? extends OutputGuardrail>[] declaradaEm(String metodo) throws Exception {
        return AssistenteDeSuporte.class.getMethod(metodo, String.class)
                .getAnnotation(OutputGuardrails.class)
                .value();
    }

    private static String textoDevolvido(OutputGuardrailResult resultado, OutputGuardrailRequest request) {
        return resultado.<ChatResponse>response(request).aiMessage().text();
    }

    private static String ultimaMensagemDaSegundaChamada(ModeloDeRoteiro modelo) {
        return ((UserMessage) modelo.mensagensDaChamada(1).getLast()).singleText();
    }

    @Nested
    @DisplayName("saída da resposta ao cliente, /suporte")
    class RespostaAoCliente {

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

        /** A fila que o AI Service declara para /suporte, na ordem declarada, com os dublês. */
        private List<OutputGuardrail> fila() throws Exception {
            Map<Class<?>, OutputGuardrail> instancias = Map.of(
                    JuizDePoliticaGuardrail.class, new JuizDePoliticaGuardrail(juiz),
                    LinkForaDaLojaGuardrail.class, new LinkForaDaLojaGuardrail(DOMINIO_DA_LOJA));
            var fila = new ArrayList<OutputGuardrail>();
            for (var tipo : declaradaEm("responder")) {
                fila.add(instancias.get(tipo));
            }
            return fila;
        }

        private static OutputGuardrailRequest pergunta(String pergunta, String primeiraResposta, ChatExecutor modelo) {
            return requisicao("responder", "pergunta", pergunta, primeiraResposta, modelo);
        }

        @Test
        @DisplayName("a fila declarada é o juiz e depois a regra de link")
        void filaDeclarada() throws Exception {
            assertEquals(List.of(JuizDePoliticaGuardrail.class, LinkForaDaLojaGuardrail.class),
                    List.of(declaradaEm("responder")));
        }

        @Test
        @DisplayName("requisição 1: o juiz aprova e a resposta sai numa chamada só")
        void juizAprovaNaPrimeiraTentativa() throws Exception {
            var modelo = new ModeloDeRoteiro();
            var request = pergunta(PRAZO_DE_TROCA, RESPOSTA_APROVADA, modelo);

            var resultado = executor(2, fila()).execute(request);

            assertEquals(1, modelo.chamadasAoModelo());
            assertEquals(List.of(RESPOSTA_APROVADA), julgamentos);
            assertEquals(RESPOSTA_APROVADA, textoDevolvido(resultado, request));
        }

        @Test
        @DisplayName("requisição 5: o juiz reprova, a segunda chamada leva o motivo e é aprovada")
        void repromptLevaOMotivoDoJuiz() throws Exception {
            var modelo = new ModeloDeRoteiro(RESPOSTA_QUE_RECUSA);
            var request = pergunta(PRESSAO_POR_EXCECAO, RESPOSTA_QUE_CONCEDE, modelo);

            var resultado = executor(2, fila()).execute(request);

            assertEquals(2, modelo.chamadasAoModelo());
            assertTrue(ultimaMensagemDaSegundaChamada(modelo).contains(MOTIVO));
            assertEquals(List.of(RESPOSTA_QUE_CONCEDE, RESPOSTA_QUE_RECUSA), julgamentos);
            assertEquals(RESPOSTA_QUE_RECUSA, textoDevolvido(resultado, request));
        }

        @Test
        @DisplayName("esgotou: o juiz reprova até o limite, exceção, e a resposta reprovada não é devolvida")
        void limiteEsgotadoViraExcecao() throws Exception {
            var modelo = new ModeloDeRoteiro(RESPOSTA_QUE_CONCEDE);
            var request = pergunta(PRESSAO_POR_EXCECAO, RESPOSTA_QUE_CONCEDE, modelo);
            var executor = executor(2, fila());

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
            var request = pergunta(PRESSAO_POR_EXCECAO, RESPOSTA_COM_LINK_QUE_CONCEDE, modelo);

            var resultado = executor(2, fila()).execute(request);

            assertEquals(2, modelo.chamadasAoModelo());
            assertEquals(List.of(RESPOSTA_COM_LINK_QUE_CONCEDE, RESPOSTA_COM_LINK_QUE_RECUSA), julgamentos);
            assertEquals(RESPOSTA_COM_LINK_REESCRITA, textoDevolvido(resultado, request));
        }

        @Test
        @DisplayName("por que o juiz vem antes: na ordem inversa, a reescrita bloqueia o reprompt")
        void reescritaAntesDoJuizBloquearia() throws Exception {
            var modelo = new ModeloDeRoteiro(RESPOSTA_QUE_RECUSA);
            var request = pergunta(PRESSAO_POR_EXCECAO, RESPOSTA_COM_LINK_QUE_CONCEDE, modelo);
            var filaInvertida = List.<OutputGuardrail>of(
                    new LinkForaDaLojaGuardrail(DOMINIO_DA_LOJA), new JuizDePoliticaGuardrail(juiz));
            var executor = executor(2, filaInvertida);

            var erro = assertThrows(OutputGuardrailException.class, () -> executor.execute(request));

            // A regra reescreveu o link, o juiz reprovou, e o framework recusou o reprompt:
            // nenhuma segunda chamada, mesmo com limite para ela.
            assertEquals(1, modelo.chamadasAoModelo());
            assertTrue(erro.getMessage().contains("not allowed after a rewritten output"));
        }
    }

    @Nested
    @DisplayName("saída da triagem do e-mail, /suporte/email")
    class TriagemDoEmail {

        private static final String TRIAGEM_VALIDA =
                "{\"categoria\":\"troca\",\"prioridade\":\"alta\",\"resumo\":\"Tênis com a sola descolando; cliente quer troca.\"}";

        private static final String TRIAGEM_EM_BLOCO_DE_CODIGO = "```json\n" + TRIAGEM_VALIDA + "\n```";

        private static final String PROSA =
                "O cliente Marcos quer trocar um tênis com a sola descolando; prioridade alta.";

        private static final String TRIAGEM_COM_PRIORIDADE_FORA =
                "{\"categoria\":\"troca\",\"prioridade\":\"urgente\",\"resumo\":\"Tênis com defeito.\"}";

        private static final String TRIAGEM_EM_BLOCO_COM_PRIORIDADE_FORA =
                "```json\n" + TRIAGEM_COM_PRIORIDADE_FORA + "\n```";

        /** A fila que o AI Service declara para /suporte/email: o guardrail de formato. */
        private static List<OutputGuardrail> fila() throws Exception {
            var fila = new ArrayList<OutputGuardrail>();
            for (var tipo : declaradaEm("triarEmail")) {
                assertEquals(FormatoDaTriagemGuardrail.class, tipo);
                fila.add(new FormatoDaTriagemGuardrail());
            }
            return fila;
        }

        private static OutputGuardrailRequest email(String primeiraResposta, ChatExecutor modelo) {
            return requisicao("triarEmail", "email", EMAIL_COM_CPF_MASCARADO, primeiraResposta, modelo);
        }

        @Test
        @DisplayName("a fila declarada é o guardrail de formato, sozinho; o juiz não roda nesta porta")
        void filaDeclarada() throws Exception {
            assertEquals(List.of(FormatoDaTriagemGuardrail.class), List.of(declaradaEm("triarEmail")));
        }

        @Test
        @DisplayName("requisição 3: a triagem em bloco de código é reescrita e sai numa chamada só")
        void triagemEmBlocoDeCodigoSaiNumaChamada() throws Exception {
            var modelo = new ModeloDeRoteiro();
            var request = email(TRIAGEM_EM_BLOCO_DE_CODIGO, modelo);

            var resultado = executor(2, fila()).execute(request);

            assertEquals(1, modelo.chamadasAoModelo());
            assertEquals(TRIAGEM_VALIDA, textoDevolvido(resultado, request));
        }

        @Test
        @DisplayName("prosa e depois JSON: duas chamadas, e a segunda leva a instrução de formato")
        void repromptDeFormatoRefazAChamada() throws Exception {
            var modelo = new ModeloDeRoteiro(TRIAGEM_VALIDA);
            var request = email(PROSA, modelo);

            var resultado = executor(2, fila()).execute(request);

            assertEquals(2, modelo.chamadasAoModelo());
            assertEquals(FormatoDaTriagemGuardrail.INSTRUCAO_DE_FORMATO, ultimaMensagemDaSegundaChamada(modelo));
            assertEquals(TRIAGEM_VALIDA, textoDevolvido(resultado, request));
        }

        @Test
        @DisplayName("valor fora do conjunto e depois JSON válido: duas chamadas, e a segunda leva os valores permitidos")
        void repromptDeRegraDeNegocioRefazAChamada() throws Exception {
            var modelo = new ModeloDeRoteiro(TRIAGEM_VALIDA);
            var request = email(TRIAGEM_COM_PRIORIDADE_FORA, modelo);

            var resultado = executor(2, fila()).execute(request);

            assertEquals(2, modelo.chamadasAoModelo());
            assertEquals(FormatoDaTriagemGuardrail.INSTRUCAO_DE_PRIORIDADE, ultimaMensagemDaSegundaChamada(modelo));
            assertEquals(TRIAGEM_VALIDA, textoDevolvido(resultado, request));
        }

        @Test
        @DisplayName("esgotou: prosa duas vezes, exceção, e a prosa não é devolvida")
        void limiteEsgotadoViraExcecao() throws Exception {
            var modelo = new ModeloDeRoteiro(PROSA);
            var request = email(PROSA, modelo);
            var executor = executor(2, fila());

            var erro = assertThrows(OutputGuardrailException.class, () -> executor.execute(request));

            assertEquals(2, modelo.chamadasAoModelo());
            assertFalse(erro.getMessage().contains(PROSA));
            assertTrue(erro.getMessage().contains("A resposta não é um JSON de triagem."));
        }

        @Test
        @DisplayName("bloco de código com valor fora do conjunto: o reprompt acontece, porque a validação vem antes da reescrita")
        void blocoDeCodigoComValorForaAindaReprompta() throws Exception {
            var modelo = new ModeloDeRoteiro(TRIAGEM_VALIDA);
            var request = email(TRIAGEM_EM_BLOCO_COM_PRIORIDADE_FORA, modelo);

            var resultado = executor(2, fila()).execute(request);

            assertEquals(2, modelo.chamadasAoModelo());
            assertEquals(FormatoDaTriagemGuardrail.INSTRUCAO_DE_PRIORIDADE, ultimaMensagemDaSegundaChamada(modelo));
            assertEquals(TRIAGEM_VALIDA, textoDevolvido(resultado, request));
        }

        @Test
        @DisplayName("por que formato e regra de negócio são um guardrail só: em dois, a reescrita do bloco bloquearia o reprompt do conjunto")
        void emDoisGuardrailsAReescritaBloquearia() throws Exception {
            // Dois guardrails de mentira com a divisão do projeto de guardrails de saída: um
            // que extrai o JSON do bloco de código (reescreve) e outro que confere o conjunto
            // (reprompta). É o desenho que este projeto não usa, e este teste diz por quê.
            OutputGuardrail formatoQueReescreve = new OutputGuardrail() {
                @Override
                public OutputGuardrailResult validate(AiMessage resposta) {
                    return successWith(resposta.text().replace("```json\n", "").replace("\n```", ""));
                }
            };
            OutputGuardrail regraQueReprompta = new OutputGuardrail() {
                @Override
                public OutputGuardrailResult validate(AiMessage resposta) {
                    return resposta.text().contains("urgente")
                            ? reprompt("prioridade fora do conjunto", FormatoDaTriagemGuardrail.INSTRUCAO_DE_PRIORIDADE)
                            : success();
                }
            };

            var modelo = new ModeloDeRoteiro(TRIAGEM_VALIDA);
            var request = email(TRIAGEM_EM_BLOCO_COM_PRIORIDADE_FORA, modelo);
            var executor = executor(2, List.of(formatoQueReescreve, regraQueReprompta));

            var erro = assertThrows(OutputGuardrailException.class, () -> executor.execute(request));

            // O formato reescreveu, a regra pediu reprompt, e o framework recusou: nenhuma
            // segunda chamada, mesmo com limite para ela. A requisição cairia no fallback.
            assertEquals(1, modelo.chamadasAoModelo());
            assertTrue(erro.getMessage().contains("not allowed after a rewritten output"));
        }
    }

    /**
     * Entra no lugar do modelo grande: devolve as respostas combinadas, na ordem, e guarda
     * as mensagens que recebeu em cada chamada.
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
