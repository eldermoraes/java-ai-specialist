package com.eldermoraes.dto;

/**
 * Resultado final do balcão de decisões, montado pelo {@code @Output} do workflow
 * a partir do que cada agente deixou no {@code AgenticScope}.
 *
 * <p>É o retrato das quatro etapas: o que o repositório disse ({@code analiseRepo}),
 * o que o ecossistema disse ({@code visaoEcossistema}), o que o humano decidiu no gate
 * ({@code aprovacao}) e o que foi (ou não foi) gravado no disco ({@code registro}).
 */
public record DecisaoDesk(
        String pergunta,
        String analiseRepo,
        String visaoEcossistema,
        String aprovacao,
        String registro) {
}
