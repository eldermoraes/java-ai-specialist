package com.eldermoraes.vendedor.ws;

import com.eldermoraes.vendedor.catalogo.Produto;
import com.eldermoraes.vendedor.dto.MensagemNegociacao;
import com.eldermoraes.vendedor.dto.RespostaVendedor;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
@WebSocket(path = "/ws/dashboard")
public class VendedorDashboard {

    private static final Logger LOG = Logger.getLogger(VendedorDashboard.class);
    private static final String ENDPOINT_ID = "com.eldermoraes.vendedor.ws.VendedorDashboard";

    @Inject
    OpenConnections openConnections;

    @OnOpen
    public void onOpen() {
    }

    public void broadcastRodada(MensagemNegociacao mensagem, Produto produto, RespostaVendedor resposta) {
        DashboardEvent event = new DashboardEvent("RODADA", mensagem, produto, resposta);
        for (WebSocketConnection c : openConnections.findByEndpointId(ENDPOINT_ID)) {
            try {
                c.sendText(event).await().indefinitely();
            } catch (Exception e) {
                LOG.warn("Falha enviando evento dashboard", e);
            }
        }
    }

    public record DashboardEvent(
            String type,
            MensagemNegociacao mensagem,
            Produto produto,
            RespostaVendedor resposta) {
    }
}
