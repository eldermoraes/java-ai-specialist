package com.eldermoraes.hitl;

import com.eldermoraes.dto.DeskEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * O bloqueio do gate humano, em Java puro: a mesma engrenagem da aprovação de desconto
 * (módulo de Agentes), aqui na versão simplificada (decisão binária, sim/não, sem banco).
 *
 * <p>A virtual thread do workflow chama {@link #aguardarDecisao(String)} e fica presa num
 * {@link CompletableFuture} até o humano responder pelo WebSocket. É a fronteira que segura o
 * {@code write_file} destrutivo até o "sim".
 *
 * <p>Uma pendência por vez: esta é uma demo mono-usuário. Em produção você teria um mapa de
 * waiters por sessão (como na aula de aprovação de desconto).
 */
@ApplicationScoped
public class ApprovalService {

    private static final Logger LOG = Logger.getLogger(ApprovalService.class);

    // Endpoint id do WebSocket = nome completo da classe anotada com @WebSocket.
    private static final String DESK_ENDPOINT = "com.eldermoraes.DeskWebsocket";

    @Inject
    OpenConnections openConnections;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "desk.aprovacao.timeout.minutos", defaultValue = "10")
    int timeoutMinutos;

    private final AtomicReference<CompletableFuture<String>> pendente = new AtomicReference<>();

    /**
     * Registra a pendência, avisa a UI e bloqueia até o humano decidir (ou estourar o timeout).
     * Fail-safe: sem resposta humana, retorna "nao" e nada é gravado.
     */
    public String aguardarDecisao(String proposta) {
        CompletableFuture<String> future = new CompletableFuture<>();
        pendente.set(future);
        broadcast(DeskEvent.aguardandoAprovacao(proposta));
        try {
            return normalizar(future.get(timeoutMinutos, TimeUnit.MINUTES));
        } catch (TimeoutException e) {
            LOG.warnf("Gate humano expirou após %d min sem resposta — negando por segurança (nada será gravado).",
                    timeoutMinutos);
            return "nao";
        } catch (Exception e) {
            LOG.error("Falha aguardando decisão humana — negando por segurança.", e);
            return "nao";
        } finally {
            pendente.set(null);
        }
    }

    /** Chamado pelo WebSocket quando o humano responde. Libera a thread bloqueada. */
    public boolean decidir(String resposta) {
        CompletableFuture<String> future = pendente.get();
        if (future != null) {
            return future.complete(resposta);
        }
        LOG.warn("Decisão recebida, mas não havia gate pendente.");
        return false;
    }

    public boolean temPendente() {
        CompletableFuture<String> future = pendente.get();
        return future != null && !future.isDone();
    }

    private String normalizar(String resposta) {
        String r = resposta == null ? "" : resposta.trim().toLowerCase();
        return (r.equals("sim") || r.equals("s")) ? "sim" : "nao";
    }

    private void broadcast(DeskEvent event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            LOG.error("Falha serializando evento de aprovação", e);
            return;
        }
        for (WebSocketConnection c : openConnections.findByEndpointId(DESK_ENDPOINT)) {
            try {
                c.sendText(json).await().indefinitely();
            } catch (Exception e) {
                LOG.warnf(e, "Falha enviando evento à conexão %s", c.id());
            }
        }
    }
}
