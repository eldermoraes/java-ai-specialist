package com.eldermoraes.hitl;

import com.eldermoraes.dto.ApprovalDecision;
import com.eldermoraes.dto.GerenteEvent;
import com.eldermoraes.dto.PropostaDesconto;
import com.eldermoraes.dto.PropostaView;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@ApplicationScoped
public class ApprovalService {

    private static final Logger LOG = Logger.getLogger(ApprovalService.class);
    private static final String GERENTE_ENDPOINT = "com.eldermoraes.ws.GerenteWebSocket";

    @Inject
    OpenConnections openConnections;

    @ConfigProperty(name = "hitl.approval.timeout.minutes", defaultValue = "10")
    int timeoutMinutes;

    private final ConcurrentMap<Long, CompletableFuture<ApprovalDecision>> waiters = new ConcurrentHashMap<>();

    void onStart(@Observes StartupEvent ev) {
        List<PropostaView> pendentes = listarPendentes();
        if (!pendentes.isEmpty()) {
            LOG.infof("Recuperação pós-startup: %d propostas pendentes encontradas", pendentes.size());
        }
    }

    @Transactional
    public ApprovalProposal criarPendente(String vendedorId, String descricaoPedido, PropostaDesconto proposta) {
        ApprovalProposal entity = new ApprovalProposal();
        entity.vendedorId = vendedorId;
        entity.descricaoPedido = descricaoPedido;
        entity.percentualProposto = proposta.percentual();
        entity.condicoes = proposta.condicoes();
        entity.justificativaAgente = proposta.justificativa();
        entity.status = ApprovalStatus.PENDENTE;
        entity.criadoEm = Instant.now();
        entity.persist();
        waiters.put(entity.id, new CompletableFuture<>());
        broadcastGerentes(GerenteEvent.nova(PropostaView.from(entity)));
        return entity;
    }

    public ApprovalDecision aguardarDecisao(Long propostaId) throws TimeoutException, InterruptedException {
        CompletableFuture<ApprovalDecision> future = waiters.get(propostaId);
        if (future == null) {
            throw new IllegalStateException("Sem waiter para proposta " + propostaId);
        }
        try {
            return future.get(timeoutMinutes, TimeUnit.MINUTES);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new RuntimeException(e.getCause());
        } finally {
            waiters.remove(propostaId);
        }
    }

    @Transactional
    public ApprovalProposal decidir(ApprovalDecision decision) {
        ApprovalProposal entity = ApprovalProposal.findById(decision.propostaId());
        if (entity == null) {
            throw new IllegalArgumentException("Proposta " + decision.propostaId() + " não encontrada");
        }
        entity.status = decision.status();
        entity.percentualFinal = decision.status() == ApprovalStatus.APROVADA
                ? entity.percentualProposto
                : decision.percentualFinal();
        entity.observacaoGerente = decision.observacao();
        entity.decididoEm = Instant.now();
        entity.persist();

        broadcastGerentes(GerenteEvent.atualizada(PropostaView.from(entity)));

        CompletableFuture<ApprovalDecision> future = waiters.get(decision.propostaId());
        if (future != null) {
            future.complete(decision);
        } else {
            LOG.warnf("Decisão chegou mas sem waiter (provavelmente após restart) para proposta %d",
                    decision.propostaId());
        }
        return entity;
    }

    @Transactional
    public PropostaView buscarPorId(Long id) {
        ApprovalProposal entity = ApprovalProposal.findById(id);
        return entity == null ? null : PropostaView.from(entity);
    }

    @Transactional
    public List<PropostaView> listarPendentes() {
        return ApprovalProposal.<ApprovalProposal>find("status", ApprovalStatus.PENDENTE).list()
                .stream().map(PropostaView::from).toList();
    }

    @Transactional
    public List<PropostaView> listarTodas() {
        return ApprovalProposal.<ApprovalProposal>findAll().list()
                .stream().map(PropostaView::from).toList();
    }

    private void broadcastGerentes(GerenteEvent event) {
        for (WebSocketConnection c : openConnections.findByEndpointId(GERENTE_ENDPOINT)) {
            try {
                c.sendText(event).await().indefinitely();
            } catch (Exception e) {
                LOG.warnf(e, "Falha enviando evento ao gerente %s", c.id());
            }
        }
    }
}
