package com.eldermoraes.workflow;

import com.eldermoraes.dto.DecisaoDesk;
import com.eldermoraes.dto.DeskEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Ponte entre o WebSocket e o workflow agêntico.
 *
 * <p>Roda a sequência numa virtual thread e vai emitindo {@link DeskEvent} para a UI. A
 * chamada {@code decidir(...)} bloqueia no gate humano. Durante esse bloqueio, o
 * {@link com.eldermoraes.hitl.ApprovalService} faz o broadcast do evento de aprovação para
 * a mesma conexão, e a resposta do humano libera a thread. Mesmo padrão de streaming do
 * roteamento de tickets e da aprovação de desconto.
 */
@ApplicationScoped
public class DeskWorkflow {

    private static final Logger LOG = Logger.getLogger(DeskWorkflow.class);

    @Inject
    DecisionDeskAgent decisionDeskAgent;

    public Multi<DeskEvent> processar(String pergunta) {
        return Multi.createFrom().emitter(emitter ->
                Thread.startVirtualThread(() -> run(pergunta, emitter)));
    }

    private void run(String pergunta, MultiEmitter<? super DeskEvent> emitter) {
        try {
            emitter.emit(DeskEvent.recebido());
            DecisaoDesk decisao = decisionDeskAgent.decidir(pergunta);
            emitter.emit(DeskEvent.decidida(decisao));
            emitter.complete();
        } catch (Exception e) {
            LOG.error("Falha no balcão de decisões", e);
            emitter.emit(DeskEvent.erro(e.getMessage()));
            emitter.complete();
        }
    }
}
