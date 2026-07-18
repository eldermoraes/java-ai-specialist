package com.eldermoraes.dto;

import java.util.List;

/**
 * Wrapper para o {@code structuredContent} da tool {@code listar_pedidos_por_cliente}.
 *
 * <p>Por que não devolver direto uma {@code List<Pedido>}? Porque o topo do
 * {@code structuredContent} no MCP é sempre um objeto (um JSON com chaves), nunca um
 * array solto. Então embrulhamos a lista num record com o nome do cliente e a coleção de
 * pedidos. De quebra, a resposta fica autoexplicativa: o modelo do outro lado lê
 * {@code cliente} e {@code pedidos} e sabe exatamente o que recebeu.
 */
public record PedidosDoCliente(
        String cliente,
        List<Pedido> pedidos) {
}
