package com.eldermoraes.workflow;

import com.eldermoraes.dto.ComiteEvent;
import com.eldermoraes.dto.Deliberacao;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ComiteOrchestrator {

    private static final Logger LOG = Logger.getLogger(ComiteOrchestrator.class);

    @Inject
    ComiteAgent comiteAgent;

    public Multi<ComiteEvent> deliberarAsStream(String dossie) {
        return Multi.createFrom().emitter(emitter ->
                Thread.startVirtualThread(() -> runDeliberacao(dossie, emitter)));
    }

    private void runDeliberacao(String dossie, MultiEmitter<? super ComiteEvent> emitter) {
        try {
            emitter.emit(ComiteEvent.recebido(preview(dossie)));

            Deliberacao deliberacao = comiteAgent.deliberar(dossie);

            LOG.infof(">> decisao=%s placar=%s votos=%d",
                    deliberacao.resultado().decisaoFinal(),
                    deliberacao.resultado().placar(),
                    deliberacao.resultado().votos().size());

            emitter.emit(ComiteEvent.votacao(deliberacao.resultado()));
            emitter.emit(ComiteEvent.parecer(deliberacao.parecer()));
            emitter.complete();
        } catch (Exception e) {
            LOG.error("Falha na deliberação do comitê", e);
            emitter.fail(e);
        }
    }

    private String preview(String dossie) {
        if (dossie == null) {
            return "";
        }
        return dossie.length() <= 80 ? dossie : dossie.substring(0, 80) + "…";
    }
}
