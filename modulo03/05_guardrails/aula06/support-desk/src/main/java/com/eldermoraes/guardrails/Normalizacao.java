package com.eldermoraes.guardrails;

import java.text.Normalizer;
import java.util.Locale;

/**
 * A mesma normalização para as três listas do projeto (escopo, filtro e conjunto de
 * valores da triagem): minúsculas, sem acento e com um espaço só entre as palavras. Sem
 * isso, "Ignore TODAS as orientacoes" escaparia de "ignore todas as orientações" por um
 * detalhe de digitação, e "Média" não seria "media".
 */
final class Normalizacao {

    private Normalizacao() {
    }

    static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
