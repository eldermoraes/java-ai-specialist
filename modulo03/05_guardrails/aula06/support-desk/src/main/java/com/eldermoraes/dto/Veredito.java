package com.eldermoraes.dto;

/**
 * O veredito do juiz, em campos que o código lê.
 *
 * Binário de propósito: aprovada ou não. Nota de 1 a 5 esconderia a decisão, e a
 * decisão é o que o guardrail precisa. O motivo serve a duas coisas: vai para o log, e
 * vira a mensagem do reprompt quando a resposta é reprovada.
 */
public record Veredito(boolean aprovada, String motivo) {

    public static Veredito aprovar() {
        return new Veredito(true, "");
    }

    public static Veredito reprovar(String motivo) {
        return new Veredito(false, motivo);
    }
}
