package com.eldermoraes.workflow;

import com.eldermoraes.ai.CulturalTranslator;
import com.eldermoraes.dto.Idioma;
import com.eldermoraes.dto.ProgressUpdate;
import com.eldermoraes.dto.Traducao;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.concurrent.StructuredTaskScope;

@ApplicationScoped
public class TraducaoOrchestrator {

    @Inject
    CulturalTranslator translator;

    public Multi<ProgressUpdate> traduzir(String comunicado) {
        return Multi.createFrom().emitter(emitter ->
                Thread.startVirtualThread(() -> runTraducao(comunicado, emitter)));
    }

    private void runTraducao(String comunicado,
                             MultiEmitter<? super ProgressUpdate> emitter) {
        try {
            List<Idioma> idiomas = Idioma.alvos();
            emitter.emit(ProgressUpdate.started(idiomas.stream().map(Idioma::codigo).toList()));

            try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<Void>awaitAll())) {
                for (Idioma idioma : idiomas) {
                    scope.fork(() -> {
                        runWorker(idioma, comunicado, emitter);
                        return null;
                    });
                }
                scope.join();
            }

            emitter.emit(ProgressUpdate.allDone());
            emitter.complete();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitter.fail(e);
        } catch (Exception e) {
            emitter.fail(e);
        }
    }

    private void runWorker(Idioma idioma, String comunicado,
                           MultiEmitter<? super ProgressUpdate> emitter) {
        long start = System.currentTimeMillis();
        try {
            Traducao traducao = translator.traduzir(idioma.nome(), idioma.codigo(),
                    idioma.paisAlvo(), comunicado);
            long elapsed = System.currentTimeMillis() - start;
            Traducao result = traducao == null
                    ? Traducao.failure(idioma.codigo(), "resposta vazia do modelo")
                    : traducao;
            emitter.emit(ProgressUpdate.langDone(idioma.codigo(), result, elapsed));
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            emitter.emit(ProgressUpdate.langError(idioma.codigo(),
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    elapsed));
        }
    }
}
