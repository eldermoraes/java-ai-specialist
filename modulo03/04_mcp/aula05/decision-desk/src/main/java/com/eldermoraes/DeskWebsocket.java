package com.eldermoraes;

import com.eldermoraes.dto.DeskEvent;
import com.eldermoraes.hitl.ApprovalService;
import com.eldermoraes.workflow.DeskWorkflow;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Set;

/**
 * O canal único da UI. A mesma conexão serve para dois papéis, decididos pelo estado do gate:
 * quando há um gate pendente e o texto é uma resposta binária, é a decisão do humano; caso
 * contrário, é uma nova pergunta para o balcão.
 */
@WebSocket(path = "/ws/desk")
public class DeskWebsocket {

    private static final Logger LOG = Logger.getLogger(DeskWebsocket.class);
    private static final Set<String> RESPOSTAS = Set.of("sim", "s", "nao", "não", "n");

    @Inject
    DeskWorkflow workflow;

    @Inject
    ApprovalService approvalService;

    @OnTextMessage
    public Multi<DeskEvent> onMessage(String mensagem) {
        String texto = mensagem == null ? "" : mensagem.trim();
        String normalizada = texto.toLowerCase();

        // Se um gate está aberto e o texto é sim/não, isto é a decisão do humano, não uma pergunta.
        if (approvalService.temPendente() && RESPOSTAS.contains(normalizada)) {
            approvalService.decidir(normalizada);
            return Multi.createFrom().item(DeskEvent.aprovacaoRecebida(normalizada));
        }

        return workflow.processar(texto);
    }

    @OnError
    public DeskEvent onError(Throwable t) {
        LOG.error("Falha no balcão de decisões", t);
        return DeskEvent.erro(t.getMessage());
    }
}
