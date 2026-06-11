package com.eldermoraes.dto;

/**
 * Foto final do quadro da war-room, montada pelo @Output a partir do AgenticScope.
 */
public record QuadroFinal(
        String evidenciaAplicacao,
        String evidenciaInfra,
        String evidenciaBanco,
        String hipotese,
        RelatorioIncidente relatorio) {
}
