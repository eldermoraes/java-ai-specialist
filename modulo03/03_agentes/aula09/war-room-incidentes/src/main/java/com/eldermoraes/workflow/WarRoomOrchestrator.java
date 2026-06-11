package com.eldermoraes.workflow;

import com.eldermoraes.dto.Incidente;
import com.eldermoraes.dto.QuadroFinal;
import com.eldermoraes.dto.WarRoomEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class WarRoomOrchestrator {

    private static final Logger LOG = Logger.getLogger(WarRoomOrchestrator.class);

    @Inject
    WarRoomAgent warRoomAgent;

    @Inject
    QuadroEventBus eventBus;

    public Multi<WarRoomEvent> investigarAsStream(Incidente incidente) {
        return Multi.createFrom().emitter(emitter ->
                Thread.startVirtualThread(() -> runInvestigacao(incidente, emitter)));
    }

    private void runInvestigacao(Incidente incidente, MultiEmitter<? super WarRoomEvent> emitter) {
        String warRoomId = UUID.randomUUID().toString();
        eventBus.registrar(warRoomId, emitter::emit);
        try {
            validar(incidente);
            emitter.emit(WarRoomEvent.aberto());
            QuadroFinal quadro = warRoomAgent.investigar(
                    incidente.sintoma(), incidente.logs(), incidente.metricas(),
                    incidente.bancoDados(), warRoomId);
            if (quadro.relatorio() == null) {
                // Quiescência ou teto de passos (condições de parada 2 e 3) encerram a
                // investigação sem o goal — ex.: um agente devolveu resposta em branco e o
                // estado nunca entrou no quadro, deixando os downstream eternamente not-ready.
                LOG.warnf(">> war-room %s terminou sem relatório final; pendências no quadro: %s",
                        warRoomId, pendencias(quadro));
                emitter.emit(WarRoomEvent.error(
                        "A war-room terminou sem o relatório final (pendências no quadro: "
                                + pendencias(quadro) + "). Isso acontece quando o quadro fica "
                                + "quiescente — algum agente devolveu resposta vazia — ou quando "
                                + "o teto de passos é atingido. Dispare a investigação novamente."));
                emitter.complete();
                return;
            }
            LOG.infof(">> war-room %s encerrada: severidade=%s causaRaiz=%s",
                    warRoomId, quadro.relatorio().severidade(), quadro.relatorio().causaRaiz());
            emitter.emit(WarRoomEvent.relatorio(quadro));
            emitter.complete();
        } catch (Exception e) {
            LOG.error("Falha na investigação do incidente", e);
            // Emite o erro como item normal do stream (em vez de emitter.fail): o Multi
            // completa sem falha, a conexão WebSocket permanece aberta e o usuário pode
            // disparar a próxima investigação sem reconectar.
            emitter.emit(WarRoomEvent.error(e.getMessage()));
            emitter.complete();
        } finally {
            eventBus.remover(warRoomId);
        }
    }

    private void validar(Incidente incidente) {
        // Campo blank nunca entra no quadro (hasState exige não-blank) -> agente nunca fica ready
        // -> quiescência sem resultado. Melhor falhar rápido com mensagem clara.
        if (isBlank(incidente.sintoma()) || isBlank(incidente.logs())
                || isBlank(incidente.metricas()) || isBlank(incidente.bancoDados())) {
            throw new IllegalArgumentException(
                    "Preencha os 4 campos do incidente: sintoma, logs, métricas e banco de dados");
        }
    }

    private String pendencias(QuadroFinal quadro) {
        List<String> faltam = new ArrayList<>();
        if (isBlank(quadro.evidenciaAplicacao())) {
            faltam.add("evidenciaAplicacao");
        }
        if (isBlank(quadro.evidenciaInfra())) {
            faltam.add("evidenciaInfra");
        }
        if (isBlank(quadro.evidenciaBanco())) {
            faltam.add("evidenciaBanco");
        }
        if (isBlank(quadro.hipotese())) {
            faltam.add("hipotese");
        }
        faltam.add("relatorioIncidente");
        return String.join(", ", faltam);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
