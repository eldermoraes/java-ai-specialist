package com.eldermoraes.workflow;

import com.eldermoraes.ai.AnalistaComercial;
import com.eldermoraes.ai.AnalistaCompliance;
import com.eldermoraes.ai.AnalistaGarantias;
import com.eldermoraes.ai.AnalistaRisco;
import com.eldermoraes.ai.AnalistaSetorial;
import com.eldermoraes.dto.DecisaoVoto;
import com.eldermoraes.dto.ResultadoComite;
import com.eldermoraes.dto.Voto;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.voting.VotingPlanner;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.service.V;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public interface ComiteDeCredito {

    @PlannerAgent(name = "comiteCredito",
            description = "Comitê de crédito: 5 analistas independentes votam sobre o mesmo dossiê",
            outputKey = "resultadoComite",
            subAgents = {
                    AnalistaRisco.class,
                    AnalistaComercial.class,
                    AnalistaCompliance.class,
                    AnalistaGarantias.class,
                    AnalistaSetorial.class
            })
    ResultadoComite deliberar(@V("dossie") String dossie);

    /**
     * O framework chama este Supplier A CADA deliberação: cada execução ganha um
     * VotingPlanner novo (o planner é stateful — acumula os votos).
     */
    @PlannerSupplier
    static Planner planner() {
        return new VotingPlanner(ComiteDeCredito::consolidar);
    }

    /**
     * O "regimento do comitê" como VotingStrategy custom:
     * maioria simples; em empate, prevalece a decisão mais conservadora
     * (ordem do enum: APROVAR < APROVAR_COM_RESSALVAS < NEGAR).
     */
    static ResultadoComite consolidar(Collection<Object> votes) {
        List<Voto> votos = votes.stream().map(Voto.class::cast).toList();

        Map<DecisaoVoto, Long> contagem = votos.stream()
                .collect(Collectors.groupingBy(Voto::decisao, Collectors.counting()));

        long maisVotada = contagem.values().stream().max(Long::compare).orElse(0L);

        DecisaoVoto decisaoFinal = contagem.entrySet().stream()
                .filter(e -> e.getValue() == maisVotada)
                .map(Map.Entry::getKey)
                .max(Comparator.comparingInt(Enum::ordinal))   // desempate: a mais conservadora
                .orElse(DecisaoVoto.NEGAR);

        Map<String, Long> placar = new LinkedHashMap<>();
        for (DecisaoVoto d : DecisaoVoto.values()) {
            placar.put(d.name(), contagem.getOrDefault(d, 0L));
        }

        return new ResultadoComite(decisaoFinal, placar, votos);
    }
}
