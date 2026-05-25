package com.eldermoraes.workflow;

import com.eldermoraes.ai.EngineerAgent;
import com.eldermoraes.ai.FaqBot;
import com.eldermoraes.ai.ProductManagerAgent;
import com.eldermoraes.ai.SecurityOfficer;
import com.eldermoraes.ai.TicketClassifier;
import com.eldermoraes.dto.ModelTier;
import com.eldermoraes.dto.TicketCategory;
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
    TicketClassifier classifier;

    @Inject
    FaqBot faqBot;

    @Inject
    EngineerAgent engineer;

    @Inject
    SecurityOfficer securityOfficer;

    @Inject
    ProductManagerAgent productManager;

    public Multi<TicketEvent> routeAsStream(String ticket) {
        return Multi.createFrom().emitter(emitter ->
                Thread.startVirtualThread(() -> runRouting(ticket, emitter)));
    }

    private void runRouting(String ticket, MultiEmitter<? super TicketEvent> emitter) {
        try {
            emitter.emit(TicketEvent.received(preview(ticket)));
            TicketCategory category = classifier.classify(ticket);
            emitter.emit(TicketEvent.classification(
                    category, tierFor(category), tierFor(category).modelId(), agentNameFor(category)));
            TicketResponse response = route(ticket, category);
            emitter.emit(TicketEvent.answer(response));
            emitter.complete();
        } catch (Exception e) {
            emitter.fail(e);
        }
    }

    public TicketResponse route(String ticket, TicketCategory category) {
        long start = System.currentTimeMillis();
        return switch (category) {
            case FAQ -> {
                LOG.infof(">> Categoria: FAQ | Modelo: %s", ModelTier.FAST.modelId());
                String answer = faqBot.answer(ticket);
                yield new TicketResponse(category, ModelTier.FAST, ModelTier.FAST.modelId(),
                        "FaqBot", answer, System.currentTimeMillis() - start);
            }
            case BUG -> {
                LOG.infof(">> Categoria: BUG | Modelo: %s", ModelTier.ROBUST.modelId());
                String answer = engineer.analyze(ticket);
                yield new TicketResponse(category, ModelTier.ROBUST, ModelTier.ROBUST.modelId(),
                        "EngineerAgent", answer, System.currentTimeMillis() - start);
            }
            case SECURITY -> {
                LOG.infof(">> Categoria: SECURITY | Modelo: %s", ModelTier.ROBUST.modelId());
                String answer = securityOfficer.handle(ticket);
                yield new TicketResponse(category, ModelTier.ROBUST, ModelTier.ROBUST.modelId(),
                        "SecurityOfficer", answer, System.currentTimeMillis() - start);
            }
            case FEATURE -> {
                LOG.infof(">> Categoria: FEATURE | Modelo: %s", ModelTier.FAST.modelId());
                String answer = productManager.triage(ticket);
                yield new TicketResponse(category, ModelTier.FAST, ModelTier.FAST.modelId(),
                        "ProductManagerAgent", answer, System.currentTimeMillis() - start);
            }
        };
    }

    public ModelTier tierFor(TicketCategory category) {
        return switch (category) {
            case FAQ, FEATURE -> ModelTier.FAST;
            case BUG, SECURITY -> ModelTier.ROBUST;
        };
    }

    public String agentNameFor(TicketCategory category) {
        return switch (category) {
            case FAQ -> "FaqBot";
            case BUG -> "EngineerAgent";
            case SECURITY -> "SecurityOfficer";
            case FEATURE -> "ProductManagerAgent";
        };
    }

    private String preview(String ticket) {
        if (ticket == null) return "";
        return ticket.length() <= 80 ? ticket : ticket.substring(0, 80) + "…";
    }
}
