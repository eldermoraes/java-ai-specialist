package com.eldermoraes.workflow;

import com.eldermoraes.dto.WarRoomEvent;
import dev.langchain4j.agentic.planner.Action;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.InitPlanningContext;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.planner.PlanningContext;
import dev.langchain4j.agentic.scope.AgentInvocation;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Decorator do BlackboardPlanner sobre o SPI Planner: a cada passo, publica no
 * QuadroEventBus qual agente acabou de escrever no quadro. Toda decisão de
 * orquestração continua com o delegate — aqui é só observabilidade, e falha de
 * observabilidade NUNCA derruba a investigação (try/catch isolado).
 */
public class ObservablePlanner implements Planner {

    private static final Logger LOG = Logger.getLogger(ObservablePlanner.class);
    private static final int MAX_PREVIEW = 2000;

    private final Planner delegate;
    private final QuadroEventBus bus;

    /** name() -> outputKey() de cada knowledge source, capturado no init(). */
    private final Map<String, String> outputKeyPorAgente = new HashMap<>();

    public ObservablePlanner(Planner delegate, QuadroEventBus bus) {
        this.delegate = delegate;
        this.bus = bus;
    }

    @Override
    public void init(InitPlanningContext context) {
        for (AgentInstance subagente : context.subagents()) {
            outputKeyPorAgente.put(subagente.name(), subagente.outputKey());
        }
        delegate.init(context);
    }

    @Override
    public Action firstAction(PlanningContext context) {
        return delegate.firstAction(context);
    }

    @Override
    public Action nextAction(PlanningContext context) {
        publicarContribuicao(context);
        return delegate.nextAction(context);
    }

    private void publicarContribuicao(PlanningContext context) {
        try {
            AgentInvocation invocation = context.previousAgentInvocation();
            if (invocation == null) {
                return;
            }
            String warRoomId = String.valueOf(context.agenticScope().readState("warRoomId", ""));
            String agente = invocation.agentName();
            String outputKey = outputKeyPorAgente.get(agente);
            String conteudo = invocation.output() == null ? "" : String.valueOf(invocation.output());
            if (conteudo.length() > MAX_PREVIEW) {
                conteudo = conteudo.substring(0, MAX_PREVIEW) + "…";
            }
            LOG.infof(">> quadro[%s]: %s escreveu '%s'", warRoomId, agente, outputKey);
            bus.publicar(warRoomId, WarRoomEvent.contribuicao(agente, outputKey, conteudo));
        } catch (Exception e) {
            LOG.warn("Observabilidade do quadro falhou (investigação segue normalmente)", e);
        }
    }

    @Override
    public Map<String, Object> executionState() {
        return delegate.executionState();
    }

    @Override
    public void restoreExecutionState(Map<String, Object> state) {
        delegate.restoreExecutionState(state);
    }

    @Override
    public AgenticSystemTopology topology() {
        return delegate.topology();
    }
}
