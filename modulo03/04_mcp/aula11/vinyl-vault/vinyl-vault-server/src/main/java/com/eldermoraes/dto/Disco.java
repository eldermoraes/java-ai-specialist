package com.eldermoraes.dto;

import java.math.BigDecimal;

/**
 * Um disco de vinil do acervo do Sebo do Vini.
 *
 * <p>Este record é o coração do <b>structured output</b> deste desafio. Em vez de a tool
 * {@code buscar_disco} devolver uma {@code String} (texto que alguém do outro lado precisa
 * interpretar), ela devolve este record, e a extensão o serializa como
 * {@code structuredContent} na resposta MCP. O client recebe campos nomeados e tipados
 * ({@code id}, {@code preco}, {@code condicao}) e pode materializá-los de volta num record
 * dele, sem fatiar texto. É a mesma afinidade com Java que você sentiu na saída estruturada
 * do LLM: você trabalha com tipos, não com parsing de string.
 *
 * <p>Detalhe do domínio: no sebo, cada disco é um exemplar único. Não existe "quantidade em
 * estoque": ou o disco está no acervo, ou já foi vendido. Por isso vender é uma operação
 * destrutiva (ver {@code vender_disco}).
 */
public record Disco(
        String id,
        String artista,
        String album,
        int ano,
        String condicao,
        BigDecimal preco) {
}
