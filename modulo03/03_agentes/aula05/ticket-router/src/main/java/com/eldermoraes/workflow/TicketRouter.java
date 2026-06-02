package com.eldermoraes.workflow;

import com.eldermoraes.dto.TicketEvent;
import com.eldermoraes.dto.TicketResponse;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TicketRouter {

    private static final Logger LOG = Logger.getLogger(TicketRouter.class);

    @Inject
    TicketAgent ticketAgent;

    public Multi<TicketEvent> routeAsStream(String ticket) {
        return Multi.createFrom().emitter(emitter ->
                Thread.startVirtualThread(() -> runRouting(ticket, emitter)));
    }

    private void runRouting(String ticket, MultiEmitter<? super TicketEvent> emitter) {
        try {
            emitter.emit(TicketEvent.received(preview(ticket)));

            long start = System.currentTimeMillis();
            TicketResponse response = ticketAgent.processar(ticket);
            long elapsed = System.currentTimeMillis() - start;

            LOG.infof(">> categoria=%s tier=%s modelo=%s",
                    response.category(), response.tier(), response.modelId());

            emitter.emit(TicketEvent.classification(
                    response.category(), response.tier(), response.modelId(), response.agentName()));

            TicketResponse withElapsed = new TicketResponse(
                    response.category(), response.tier(), response.modelId(),
                    response.agentName(), response.answer(), elapsed);
            emitter.emit(TicketEvent.answer(withElapsed));
            emitter.complete();
        } catch (Exception e) {
            LOG.error("Falha no roteamento do ticket", e);
            emitter.fail(e);
        }
    }

    private String preview(String ticket) {
        if (ticket == null) return "";
        return ticket.length() <= 80 ? ticket : ticket.substring(0, 80) + "…";
    }
}
