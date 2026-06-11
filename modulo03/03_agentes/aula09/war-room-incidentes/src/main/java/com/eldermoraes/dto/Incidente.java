package com.eldermoraes.dto;

/**
 * Entradas do alerta de produção: são as quatro primeiras escritas no quadro
 * da war-room. Cada analista declara via @V qual delas lê.
 */
public record Incidente(String sintoma, String logs, String metricas, String bancoDados) {
}
