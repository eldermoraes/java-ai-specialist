package com.eldermoraes.workflow;

import com.eldermoraes.ai.EscritorAdr;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import dev.langchain4j.service.V;

/**
 * Gate duro, em código Java determinístico.
 *
 * <p>Se o humano respondeu "não" no {@link GateAprovacao}, a {@link #aprovado(String)}
 * retorna {@code false} e o {@link EscritorAdr} nem é invocado: nenhum request ao modelo,
 * nenhum tool call de escrita. A fronteira entre a leitura inofensiva e a escrita destrutiva
 * é este {@code if} em Java, não a obediência do modelo.
 */
public interface RegistroDecisao {

    @ConditionalAgent(outputKey = "registro", subAgents = { EscritorAdr.class })
    String registrar(@V("aprovacao") String aprovacao);

    @ActivationCondition(EscritorAdr.class)
    static boolean aprovado(@V("aprovacao") String aprovacao) {
        return "sim".equalsIgnoreCase(aprovacao == null ? "" : aprovacao.strip());
    }
}
