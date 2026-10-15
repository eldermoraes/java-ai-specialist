package com.eldermoraes.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Um pedido da Central de Pedidos da Cloud For You.
 *
 * <p>Este record é o coração do <b>structured output</b> desta aula. Em vez de a tool
 * {@code buscar_pedido} devolver uma {@code String} (texto que alguém do outro lado
 * precisa interpretar), ela devolve este record, e a extensão o serializa como
 * {@code structuredContent} na resposta MCP. O client recebe campos nomeados e tipados
 * ({@code id}, {@code valorTotal}, a lista de {@code itens}) e pode materializá-los de
 * volta num record dele, sem fatiar texto. É a mesma afinidade com Java que você sentiu
 * na saída estruturada do LLM: você trabalha com tipos, não com parsing de string.
 */
public record Pedido(
        String id,
        String cliente,
        List<String> itens,
        String status,
        BigDecimal valorTotal) {
}
