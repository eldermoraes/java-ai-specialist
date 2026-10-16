package com.eldermoraes.guardrails;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Ouve toda chamada a modelo e soma no contador da requisição.
 *
 * A extensão registra qualquer bean ChatModelListener em todos os modelos configurados,
 * o grande e o pequeno. O listener roda na thread da requisição, então o contador de
 * escopo de requisição está ativo; fora de uma requisição (nos testes, por exemplo) ele
 * não tem onde somar e só registra no log.
 *
 * O que entra na conta é o papel do modelo ("pequeno" ou "grande"), não o nome dele: o nome
 * é configuração e pode mudar; o papel é o que a conta de chamadas quer dizer.
 */
@ApplicationScoped
public class ContadorDeChamadasListener implements ChatModelListener {

    private static final Logger LOG = Logger.getLogger(ContadorDeChamadasListener.class);

    @Inject
    ContadorDeChamadas contador;

    @Inject
    @ConfigProperty(name = "quarkus.langchain4j.ollama.pequeno.chat-model.model-id")
    String modeloPequeno;

    @Override
    public void onRequest(ChatModelRequestContext contexto) {
        String papel = modeloPequeno.equals(contexto.chatRequest().modelName()) ? "pequeno" : "grande";

        if (Arc.container().requestContext().isActive()) {
            contador.registrar(papel);
            LOG.debugf("Chamada a modelo número %d desta requisição: modelo %s", contador.total(), papel);
        } else {
            LOG.debugf("Chamada a modelo fora de uma requisição: modelo %s", papel);
        }
    }
}
