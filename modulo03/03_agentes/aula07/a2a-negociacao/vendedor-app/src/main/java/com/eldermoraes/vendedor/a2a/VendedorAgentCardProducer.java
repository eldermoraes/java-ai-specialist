package com.eldermoraes.vendedor.a2a;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.a2aproject.sdk.server.PublicAgentCard;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.AgentSkill;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;

/**
 * Publica o Agent Card do vendedor — o "cartão de visita" do protocolo A2A,
 * servido automaticamente em GET /.well-known/agent-card.json.
 * É por ele que o comprador descobre como falar com este agente.
 */
@ApplicationScoped
public class VendedorAgentCardProducer {

    @Produces
    @PublicAgentCard
    public AgentCard agentCard(
            @ConfigProperty(name = "vendedor.a2a.public-url", defaultValue = "http://localhost:8081")
            String publicUrl) {
        return AgentCard.builder()
                .name("Vendedor B2B")
                .description("Agente vendedor B2B: negocia preço e condições de produtos do catálogo "
                        + "respeitando o preço mínimo de cada item")
                .version("1.0.0")
                .capabilities(AgentCapabilities.builder()
                        .streaming(false)
                        .pushNotifications(false)
                        .build())
                .defaultInputModes(List.of("application/json"))
                .defaultOutputModes(List.of("application/json"))
                .skills(List.of(AgentSkill.builder()
                        .id("negociar")
                        .name("Negociação de compra")
                        .description("Recebe DataPart com MensagemNegociacao {compradorId, rodada, produto, "
                                + "ultimoValorProposto, mensagem} e responde artifact com RespostaVendedor "
                                + "{produto, precoOferecido, prazoEntrega, condicoes, mensagem, limiteAtingido}")
                        .tags(List.of("negociacao", "b2b", "vendas"))
                        .build()))
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), publicUrl)))
                .build();
    }
}
