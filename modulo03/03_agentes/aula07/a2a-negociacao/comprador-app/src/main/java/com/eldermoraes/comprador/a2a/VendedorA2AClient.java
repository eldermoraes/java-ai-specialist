package com.eldermoraes.comprador.a2a;

import com.eldermoraes.comprador.dto.MensagemNegociacao;
import com.eldermoraes.comprador.dto.RespostaVendedor;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.config.ClientConfig;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfig;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Lado cliente do protocolo A2A, encapsulando o Client oficial do SDK atrás
 * de uma assinatura síncrona simples para o NegociacaoCoordinator:
 * RespostaVendedor negociar(MensagemNegociacao).
 *
 * Discovery: o Agent Card do vendedor é resolvido de /.well-known/agent-card.json
 * no primeiro uso (lazy) e cacheado. Cada chamada registra seus próprios
 * consumers, então negociações concorrentes não misturam respostas.
 */
@ApplicationScoped
public class VendedorA2AClient {

    private static final Logger LOG = Logger.getLogger(VendedorA2AClient.class);

    @ConfigProperty(name = "vendedor.a2a.url", defaultValue = "http://localhost:8081")
    String vendedorUrl;

    @ConfigProperty(name = "vendedor.a2a.timeout-segundos", defaultValue = "180")
    long timeoutSegundos;

    @Inject
    NegociacaoPayloadMapper payload;

    private volatile Client client;

    public RespostaVendedor negociar(MensagemNegociacao mensagem) {
        Client c = client();

        Message a2aMessage = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(UUID.randomUUID().toString())
                .contextId(mensagem.compradorId())   // multi-turno A2A: 1 contexto = 1 negociação
                .parts(new DataPart(payload.toMap(mensagem)))
                .build();

        // Ponte síncrona: o Client entrega a resposta via consumers; os desta
        // chamada completam um future local que a virtual thread do loop espera.
        CompletableFuture<RespostaVendedor> futuro = new CompletableFuture<>();
        try {
            c.sendMessage(a2aMessage,
                    List.of((event, card) -> completar(event, futuro)),
                    futuro::completeExceptionally,
                    null);
            return futuro.get(timeoutSegundos, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Falha na chamada A2A ao vendedor (rodada "
                    + mensagem.rodada() + "): " + e.getMessage(), e);
        }
    }

    private void completar(ClientEvent event, CompletableFuture<RespostaVendedor> futuro) {
        if (event instanceof TaskEvent taskEvent) {
            Task task = taskEvent.getTask();
            TaskState estado = task.status() == null ? null : task.status().state();
            if (estado == TaskState.TASK_STATE_FAILED) {
                futuro.completeExceptionally(new IllegalStateException(
                        "Vendedor sinalizou falha na task " + task.id()));
                return;
            }
            Optional<RespostaVendedor> resposta = extrairResposta(task);
            if (resposta.isPresent()) {
                futuro.complete(resposta.get());
            } else if (estado != null && estado.isFinal()) {
                futuro.completeExceptionally(new IllegalStateException(
                        "Task A2A finalizada sem DataPart de RespostaVendedor (estado " + estado + ")"));
            }
            // estados intermediários sem artifact: aguarda o evento final
        } else if (event instanceof MessageEvent) {
            futuro.completeExceptionally(new IllegalStateException(
                    "Vendedor respondeu Message direta; esperava Task com artifact"));
        }
    }

    private Optional<RespostaVendedor> extrairResposta(Task task) {
        if (task.artifacts() == null) {
            return Optional.empty();
        }
        for (Artifact artifact : task.artifacts()) {
            for (Part<?> part : artifact.parts()) {
                if (part instanceof DataPart dataPart && dataPart.data() instanceof Map<?, ?> dados) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> mapa = (Map<String, Object>) dados;
                    return Optional.of(payload.toResposta(mapa));
                }
            }
        }
        return Optional.empty();
    }

    private Client client() {
        Client local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    client = local = criarClient();
                }
            }
        }
        return local;
    }

    private Client criarClient() {
        try {
            AgentCard card = A2A.getAgentCard(vendedorUrl);
            LOG.infof("Agent Card resolvido: %s v%s em %s", card.name(), card.version(), vendedorUrl);
            return Client.builder(card)
                    .clientConfig(new ClientConfig.Builder()
                            .setStreaming(false)   // message/send blocking: request→response por rodada
                            .build())
                    .withTransport(JSONRPCTransport.class, new JSONRPCTransportConfig())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Vendedor A2A indisponível em " + vendedorUrl
                            + " — suba o vendedor-app (porta 8081) antes de negociar. Causa: " + e.getMessage(), e);
        }
    }

    @PreDestroy
    void fechar() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }
}
