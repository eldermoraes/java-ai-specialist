package com.eldermoraes.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;

/**
 * O formato que a aplicação lê na saída da triagem do e-mail: categoria, prioridade e
 * resumo. Os dois primeiros têm um conjunto fechado de valores, que é a regra de negócio
 * da fila de atendimento: formato certo com valor inventado não serve para nada.
 *
 * Não é DTO de endpoint. Este record existe porque o guardrail de formato precisa ler os
 * campos da resposta para validá-los, e ler campo é trabalho de desserialização.
 *
 * O leitor ignora campo que não está declarado aqui: se o modelo devolver um quarto campo,
 * a triagem continua sendo lida. Faltar um dos três é outra conversa, e quem decide o que
 * fazer nesse caso é o guardrail.
 */
public record Triagem(String categoria, String prioridade, String resumo) {

    /** As categorias que a fila de atendimento da loja conhece. */
    public static final List<String> CATEGORIAS = List.of("troca", "devolucao", "entrega", "pagamento", "outro");

    /** As prioridades que a fila de atendimento conhece. */
    public static final List<String> PRIORIDADES = List.of("baixa", "media", "alta");

    private static final ObjectMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * Lê a resposta do modelo como uma triagem.
     *
     * @throws JsonProcessingException quando o texto não é um JSON que caiba neste record
     */
    public static Triagem deJson(String texto) throws JsonProcessingException {
        return JSON.readValue(texto, Triagem.class);
    }

    /** Escreve a triagem de volta em JSON. Usado pelo guardrail que normaliza um campo. */
    public String paraJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível escrever a triagem em JSON.", e);
        }
    }
}
