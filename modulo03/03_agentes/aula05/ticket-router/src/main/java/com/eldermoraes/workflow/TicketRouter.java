package com.eldermoraes.workflow;

import com.eldermoraes.ai.TicketClassifier;
import com.eldermoraes.ai.TicketHandler;
import com.eldermoraes.dto.ModelTier;
import com.eldermoraes.dto.TicketCategory;
import com.eldermoraes.dto.TicketEvent;
import com.eldermoraes.dto.TicketResponse;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import io.quarkiverse.langchain4j.ModelName;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TicketRouter {

    private static final Logger LOG = Logger.getLogger(TicketRouter.class);

    @Inject
    TicketClassifier classifier;

    @Inject
    @ModelName("smaller")
    ChatModel modeloRapido;

    @Inject
    ChatModel modeloRobusto;

    public Multi<TicketEvent> routeAsStream(String ticket) {
        return Multi.createFrom().emitter(emitter ->
                Thread.startVirtualThread(() -> runRouting(ticket, emitter)));
    }

    private void runRouting(String ticket, MultiEmitter<? super TicketEvent> emitter) {
        try {
            emitter.emit(TicketEvent.received(preview(ticket)));

            TicketCategory categoria = classifier.classify(ticket);
            ModelTier tier = tierFor(categoria);
            ChatModel selecionado = (tier == ModelTier.FAST) ? modeloRapido : modeloRobusto;
            emitter.emit(TicketEvent.classification(categoria, tier, tier.modelId(),
                    "TicketHandler"));

            LOG.infof(">> categoria=%s tier=%s modelo=%s",
                    categoria, tier, tier.modelId());

            long start = System.currentTimeMillis();
            TicketHandler handler = AgenticServices.agentBuilder(TicketHandler.class)
                    .chatModel(selecionado)
                    .build();
            String resposta = handler.responder(categoria, ticket);
            long elapsed = System.currentTimeMillis() - start;

            TicketResponse response = new TicketResponse(
                    categoria, tier, tier.modelId(), "TicketHandler", resposta, elapsed);
            emitter.emit(TicketEvent.answer(response));
            emitter.complete();
        } catch (Exception e) {
            LOG.error("Falha no roteamento do ticket", e);
            emitter.fail(e);
        }
    }

    static ModelTier tierFor(TicketCategory category) {
        return switch (category) {
            case FAQ, FEATURE -> ModelTier.FAST;
            case BUG, SECURITY -> ModelTier.ROBUST;
        };
    }

    private String preview(String ticket) {
        if (ticket == null) return "";
        return ticket.length() <= 80 ? ticket : ticket.substring(0, 80) + "…";
    }
}
