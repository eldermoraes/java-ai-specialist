package com.eldermoraes.workflow;

import com.eldermoraes.ai.AnalistaDeAplicacao;
import com.eldermoraes.ai.AnalistaDeBancoDeDados;
import com.eldermoraes.ai.AnalistaDeInfra;
import com.eldermoraes.ai.CorrelacionadorDeHipoteses;
import com.eldermoraes.ai.EngenheiroDeCausaRaiz;
import com.eldermoraes.dto.QuadroFinal;
import com.eldermoraes.dto.RelatorioIncidente;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.PlannerAgent;
import dev.langchain4j.agentic.declarative.PlannerSupplier;
import dev.langchain4j.agentic.patterns.blackboard.BlackboardPlanner;
import dev.langchain4j.agentic.patterns.blackboard.ConflictResolutionStrategy;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.V;
import jakarta.enterprise.inject.spi.CDI;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.function.Predicate;

public interface WarRoomAgent {

    @PlannerAgent(name = "warRoom",
            description = "Conduz a war-room de incidente: especialistas colaboram num quadro compartilhado até a causa raiz",
            outputKey = "quadroFinal",
            subAgents = {
                    AnalistaDeAplicacao.class,
                    AnalistaDeInfra.class,
                    AnalistaDeBancoDeDados.class,
                    CorrelacionadorDeHipoteses.class,
                    EngenheiroDeCausaRaiz.class
            })
    QuadroFinal investigar(@V("sintoma") String sintoma,
                           @V("logs") String logs,
                           @V("metricas") String metricas,
                           @V("bancoDados") String bancoDados,
                           @V("warRoomId") String warRoomId);

    /**
     * O framework chama este Supplier A CADA investigação: cada execução ganha um
     * BlackboardPlanner novo (o planner é stateful — guarda contador de passos e
     * quais agentes já dispararam).
     */
    @PlannerSupplier
    static Planner planner() {
        QuadroEventBus bus = CDI.current().select(QuadroEventBus.class).get();
        int maxPassos = ConfigProvider.getConfig()
                .getOptionalValue("warroom.max-passos", Integer.class)
                .orElse(12);

        // Goal explícito (condição de parada nº 1): o relatório final está no quadro
        Predicate<AgenticScope> objetivo = scope -> scope.hasState("relatorioIncidente");

        // Desempate (vários agentes prontos): o DBA fura a fila se o sintoma citar o banco
        ConflictResolutionStrategy prioridade = ConflictResolutionStrategy
                .agentOfType(AnalistaDeBancoDeDados.class, scope -> {
                    String sintoma = String.valueOf(scope.readState("sintoma", "")).toLowerCase();
                    return sintoma.contains("banco") || sintoma.contains("conex")
                            || sintoma.contains("query") || sintoma.contains("sql")
                            || sintoma.contains("pool");
                })
                .or(ConflictResolutionStrategy.declarationOrder());

        return new ObservablePlanner(new BlackboardPlanner(objetivo, maxPassos, prioridade), bus);
    }

    @Output
    static QuadroFinal montarQuadro(AgenticScope scope) {
        return new QuadroFinal(
                (String) scope.readState("evidenciaAplicacao"),
                (String) scope.readState("evidenciaInfra"),
                (String) scope.readState("evidenciaBanco"),
                (String) scope.readState("hipotese"),
                (RelatorioIncidente) scope.readState("relatorioIncidente"));
    }
}
