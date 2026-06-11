package com.eldermoraes.dto;

import java.util.List;
import java.util.Map;

public record ResultadoComite(DecisaoVoto decisaoFinal, Map<String, Long> placar, List<Voto> votos) {

    /** Este texto é o que o RelatorComite "enxerga" quando o template insere {resultadoComite}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DECISÃO DO COMITÊ (por votação): ").append(decisaoFinal).append('\n');
        sb.append("PLACAR: ");
        placar.forEach((decisao, qtd) -> sb.append(decisao).append('=').append(qtd).append("  "));
        sb.append('\n').append("VOTOS INDIVIDUAIS:").append('\n');
        for (Voto voto : votos) {
            sb.append("- ").append(voto.analista()).append(": ").append(voto.decisao())
              .append(" — ").append(voto.justificativa()).append('\n');
        }
        return sb.toString();
    }
}
