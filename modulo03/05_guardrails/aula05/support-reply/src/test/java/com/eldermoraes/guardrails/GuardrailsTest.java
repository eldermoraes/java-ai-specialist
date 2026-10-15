package com.eldermoraes.guardrails;

import static com.eldermoraes.guardrails.Requisicoes.DOMINIO_DA_LOJA;
import static com.eldermoraes.guardrails.Requisicoes.FRETE_DA_DEVOLUCAO;
import static com.eldermoraes.guardrails.Requisicoes.PRAZO_DE_TROCA;
import static com.eldermoraes.guardrails.Requisicoes.PRESSAO_POR_EXCECAO;
import static com.eldermoraes.guardrails.Requisicoes.PRODUTO_PERSONALIZADO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eldermoraes.ai.JuizDePolitica;
import com.eldermoraes.dto.Veredito;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.GuardrailResult.Result;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Uma decisão por teste, sem subir o Quarkus e sem Ollama. A regra de link é código e
 * se testa direto. O juiz entra como dublê: o que se testa aqui é a decisão do guardrail
 * diante de cada veredito, não o modelo.
 */
class GuardrailsTest {

    private static final String RESPOSTA_DENTRO_DA_POLITICA =
            "Você tem até 30 dias corridos após a entrega para trocar um produto com defeito.";

    private static final String RESPOSTA_QUE_CONCEDE =
            "Como você é cliente antigo, vou abrir uma exceção e autorizar a troca.";

    private static final String MOTIVO =
            "A resposta concede exceção de prazo, que a política proíbe.";

    private final LinkForaDaLojaGuardrail regraDeLink = new LinkForaDaLojaGuardrail(DOMINIO_DA_LOJA);

    private static JuizDePolitica juizQueResponde(Veredito veredito) {
        return (pergunta, resposta) -> veredito;
    }

    /** O request como a extensão monta: a resposta do modelo e a pergunta nas variáveis. */
    private static OutputGuardrailRequest request(String pergunta, String resposta) {
        ChatExecutor semModelo = new ChatExecutor() {
            @Override
            public ChatResponse execute() {
                throw new IllegalStateException("este teste não chama o modelo");
            }

            @Override
            public ChatResponse execute(List<dev.langchain4j.data.message.ChatMessage> mensagens) {
                throw new IllegalStateException("este teste não chama o modelo");
            }
        };
        return OutputGuardrailRequest.builder()
                .responseFromLLM(ChatResponse.builder().aiMessage(AiMessage.from(resposta)).build())
                .chatExecutor(semModelo)
                .requestParams(GuardrailRequestParams.builder()
                        .userMessageTemplate("{pergunta}")
                        .variables(Map.of("pergunta", pergunta))
                        .build())
                .build();
    }

    @Test
    @DisplayName("juiz aprova: a resposta segue, sem reprompt")
    void juizAprovaEARespostaSegue() {
        var guardrail = new JuizDePoliticaGuardrail(juizQueResponde(Veredito.aprovar()));

        var resultado = guardrail.validate(request(PRAZO_DE_TROCA, RESPOSTA_DENTRO_DA_POLITICA));

        assertTrue(resultado.isSuccess());
        assertFalse(resultado.isRetry());
    }

    @Test
    @DisplayName("juiz reprova: reprompt, e o motivo do juiz é a mensagem que entra na conversa")
    void juizReprovaEOMotivoViraReprompt() {
        var guardrail = new JuizDePoliticaGuardrail(juizQueResponde(Veredito.reprovar(MOTIVO)));

        var resultado = guardrail.validate(request(PRESSAO_POR_EXCECAO, RESPOSTA_QUE_CONCEDE));

        assertEquals(Result.FATAL, resultado.result());
        assertTrue(resultado.isRetry());
        assertTrue(resultado.getReprompt().orElseThrow().contains(MOTIVO));
    }

    @Test
    @DisplayName("o juiz recebe a pergunta do cliente e a resposta do assistente, nessa ordem")
    void juizRecebePerguntaEResposta() {
        var recebido = new String[2];
        JuizDePolitica juiz = (pergunta, resposta) -> {
            recebido[0] = pergunta;
            recebido[1] = resposta;
            return Veredito.aprovar();
        };

        new JuizDePoliticaGuardrail(juiz).validate(request(PRAZO_DE_TROCA, RESPOSTA_DENTRO_DA_POLITICA));

        assertEquals(PRAZO_DE_TROCA, recebido[0]);
        assertEquals(RESPOSTA_DENTRO_DA_POLITICA, recebido[1]);
    }

    @Test
    @DisplayName("link de fora da loja é reescrito, sem nova chamada")
    void linkDeForaEhReescrito() {
        var resultado = regraDeLink.validate(AiMessage.from(
                "Veja o rastreio em https://rastreio.correios.com.br/pedido/123 e as regras em "
                        + "https://www.lojaexemplo.com.br/trocas."));

        assertEquals(Result.SUCCESS_WITH_RESULT, resultado.result());
        assertEquals("Veja o rastreio em [link removido] e as regras em "
                + "https://www.lojaexemplo.com.br/trocas.", resultado.successfulText());
    }

    @Test
    @DisplayName("link da loja e dos subdomínios dela fica como está")
    void linkDaLojaFica() {
        String texto = "Acompanhe em https://lojaexemplo.com.br/pedidos e https://ajuda.lojaexemplo.com.br.";

        var resultado = regraDeLink.validate(AiMessage.from(texto));

        assertEquals(texto, resultado.successfulText());
    }

    @Test
    @DisplayName("um domínio parecido com o da loja é de fora")
    void dominioParecidoEhDeFora() {
        var resultado = regraDeLink.validate(AiMessage.from("https://lojaexemplo.com.br.golpe.net/x"));

        assertEquals(LinkForaDaLojaGuardrail.MARCADOR, resultado.successfulText());
    }

    @Test
    @DisplayName("sem link, a regra devolve o mesmo texto: o último da fila entrega o que validou")
    void semLinkDevolveOMesmoTexto() {
        var resultado = regraDeLink.validate(AiMessage.from(RESPOSTA_DENTRO_DA_POLITICA));

        assertEquals(Result.SUCCESS_WITH_RESULT, resultado.result());
        assertEquals(RESPOSTA_DENTRO_DA_POLITICA, resultado.successfulText());
    }

    @Test
    @DisplayName("os textos das requisições dos testes são os do README")
    void textosDasRequisicoesEstaoNoReadme() throws Exception {
        String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8);

        for (String texto : List.of(PRAZO_DE_TROCA, PRODUTO_PERSONALIZADO, PRESSAO_POR_EXCECAO, FRETE_DA_DEVOLUCAO)) {
            assertTrue(readme.contains(texto), texto);
        }
    }

    @Test
    @DisplayName("o domínio dos testes é o do application.properties")
    void dominioDosTestesEhODoProjeto() throws Exception {
        var propriedades = new Properties();
        try (Reader leitor = new InputStreamReader(
                getClass().getResourceAsStream("/application.properties"), StandardCharsets.UTF_8)) {
            propriedades.load(leitor);
        }

        assertEquals(DOMINIO_DA_LOJA, propriedades.getProperty("support-reply.loja.dominio"));
    }
}
