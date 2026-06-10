package com.eldermoraes.comprador.a2a;

import com.eldermoraes.comprador.dto.MensagemNegociacao;
import com.eldermoraes.comprador.dto.RespostaVendedor;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

/**
 * Converte os records do domínio de/para o Map carregado no DataPart A2A.
 * O contrato (campos de MensagemNegociacao/RespostaVendedor) é o mesmo da
 * versão REST — só o transporte mudou.
 */
@ApplicationScoped
public class NegociacaoPayloadMapper {

    // ObjectMapper próprio: o REST do app usa JSON-B; Jackson vem transitivo do SDK A2A.
    // USE_BIG_DECIMAL_FOR_FLOATS preserva valores monetários ao converter o Map do JSON-RPC.
    // NON_NULL omite campos null do DataPart: o conversor Struct→Map do SDK 1.0.0.Final
    // (A2ACommonFieldMapper.structToMap, Collectors.toMap) lança NPE com valores null.
    private final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public Map<String, Object> toMap(MensagemNegociacao mensagem) {
        return mapper.convertValue(mensagem, new TypeReference<Map<String, Object>>() {});
    }

    public RespostaVendedor toResposta(Map<String, Object> dados) {
        return mapper.convertValue(dados, RespostaVendedor.class);
    }
}
