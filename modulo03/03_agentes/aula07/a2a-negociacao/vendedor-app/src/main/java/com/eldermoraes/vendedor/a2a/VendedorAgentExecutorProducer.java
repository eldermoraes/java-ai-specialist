package com.eldermoraes.vendedor.a2a;

import com.eldermoraes.vendedor.dto.MensagemNegociacao;
import com.eldermoraes.vendedor.dto.RespostaVendedor;
import com.eldermoraes.vendedor.negociacao.NegociacaoService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.DataPart;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.TextPart;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

/**
 * Lado servidor do protocolo A2A: o AgentExecutor é chamado pelo SDK a cada
 * message/send recebida. Ciclo de vida da task: submit → startWork →
 * addArtifact → complete (ou fail).
 */
@ApplicationScoped
public class VendedorAgentExecutorProducer {

    private static final Logger LOG = Logger.getLogger(VendedorAgentExecutorProducer.class);

    @Produces
    public AgentExecutor agentExecutor(NegociacaoService negociacao, NegociacaoPayloadMapper payload) {
        return new AgentExecutor() {

            @Override
            public void execute(RequestContext context, AgentEmitter emitter) {
                emitter.submit();
                emitter.startWork();
                try {
                    MensagemNegociacao mensagem = payload.toMensagem(extrairDataPart(context.getMessage()));
                    LOG.infof("A2A task=%s contextId=%s — rodada %d do comprador %s",
                            emitter.getTaskId(), emitter.getContextId(),
                            mensagem.rodada(), mensagem.compradorId());
                    RespostaVendedor resposta = negociacao.negociar(mensagem);
                    emitter.addArtifact(List.of(new DataPart(payload.toMap(resposta))));
                    emitter.complete();
                } catch (Exception e) {
                    LOG.error("Falha na negociação A2A", e);
                    emitter.fail(emitter.newAgentMessage(
                            List.of(new TextPart("Erro na negociação: " + e.getMessage())), null));
                }
            }

            @Override
            public void cancel(RequestContext context, AgentEmitter emitter) {
                emitter.cancel();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extrairDataPart(Message message) {
        if (message != null) {
            for (Part<?> part : message.parts()) {
                if (part instanceof DataPart dataPart && dataPart.data() instanceof Map<?, ?> dados) {
                    return (Map<String, Object>) dados;
                }
            }
        }
        throw new IllegalArgumentException("Mensagem A2A sem DataPart de MensagemNegociacao");
    }
}
