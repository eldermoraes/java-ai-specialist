package com.eldermoraes.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * O formato que a aplicação lê na saída do modelo: categoria, prioridade e resumo.
 *
 * Não é DTO de endpoint. O REST deste projeto recebe e devolve texto puro; este record
 * existe porque os guardrails precisam ler os campos da resposta para validá-los, e ler
 * campo é trabalho de desserialização.
 *
 * O leitor ignora campo que não está declarado aqui: se o modelo devolver um quarto campo,
 * a triagem continua sendo lida. Faltar um dos três é outra conversa, e quem decide o que
 * fazer nesse caso é o FormatoGuardrail.
 */
public record Triagem(String categoria, String prioridade, String resumo) {

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

    /** Escreve a triagem de volta em JSON. Usado pelo guardrail que corrige um campo. */
    public String paraJson() {
        try {
            return JSON.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível escrever a triagem em JSON.", e);
        }
    }
}
