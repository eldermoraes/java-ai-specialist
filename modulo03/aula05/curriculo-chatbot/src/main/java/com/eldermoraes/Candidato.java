package com.eldermoraes;

public record Candidato(
        String nome,
        int idade,
        String profissaoBase,
        boolean temExperienciaEmNuvem
) {}