package com.eldermoraes.dto;

import java.util.List;

/**
 * Wrapper para o {@code structuredContent} da tool {@code listar_discos_por_artista}.
 *
 * <p>Por que não devolver direto uma {@code List<Disco>}? Porque o topo do
 * {@code structuredContent} no MCP é sempre um objeto (um JSON com chaves), nunca um array
 * solto. Então embrulhamos a lista num record com o nome do artista e a coleção de discos.
 * De quebra, a resposta fica autoexplicativa: o modelo do outro lado lê {@code artista} e
 * {@code discos} e sabe exatamente o que recebeu.
 */
public record DiscosDoArtista(
        String artista,
        List<Disco> discos) {
}
