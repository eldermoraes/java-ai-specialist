package com.eldermoraes.dto;

import java.util.List;

/**
 * Saída tipada do EngenheiroDeCausaRaiz — quando esta chave aparece no quadro,
 * o goal predicate do BlackboardPlanner é satisfeito e a war-room encerra.
 */
public record RelatorioIncidente(
        String causaRaiz,
        Severidade severidade,
        List<String> acoesImediatas,
        List<String> prevencao,
        String resumoExecutivo) {
}
