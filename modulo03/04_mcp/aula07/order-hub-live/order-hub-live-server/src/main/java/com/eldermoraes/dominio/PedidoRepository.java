package com.eldermoraes.dominio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.eldermoraes.dto.Pedido;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repositório fake, em memória, com meia dúzia de pedidos de exemplo.
 *
 * <p>O foco desta aula é o protocolo, não a persistência: por isso não há banco de dados
 * nem JPA aqui, só um mapa. Mas repare numa decisão que não é acidental: usamos um
 * {@link ConcurrentHashMap}, e não um {@code HashMap} comum.
 *
 * <p>Por quê? Porque um MCP server remoto é um serviço HTTP concorrente: um único server
 * atende N agentes ao mesmo tempo, cada um numa thread. Esse é o mesmo instinto de
 * thread-safety que você já tem de qualquer serviço web, e ele casa direto com a virada
 * stateless que está chegando na spec (RC de 28/07/2026): tool stateless é também tool
 * thread-safe, porque cada chamada é autossuficiente e o estado compartilhado (este mapa)
 * precisa aguentar acesso concorrente. Cuidado com estado mutável num bean singleton: aqui
 * o {@code ConcurrentHashMap} é justamente esse cuidado materializado.
 */
@ApplicationScoped
public class PedidoRepository {

    private final Map<String, Pedido> pedidos = new ConcurrentHashMap<>();

    @PostConstruct
    void carregar() {
        // Clientes fictícios com repetição de propósito: assim listar_pedidos_por_cliente
        // tem o que listar (um cliente com vários pedidos). Itens = serviços de nuvem B2B.
        adicionar(new Pedido("PED-1001", "TechNova",
                List.of("VM Standard 4vCPU", "Object Storage 1TB"),
                "ABERTO", new BigDecimal("1290.00")));
        adicionar(new Pedido("PED-1002", "DataPrime",
                List.of("Backup Gerenciado", "VM Standard 4vCPU"),
                "FATURADO", new BigDecimal("2140.50")));
        adicionar(new Pedido("PED-1003", "TechNova",
                List.of("Load Balancer", "Object Storage 1TB", "CDN Global"),
                "ENVIADO", new BigDecimal("3475.90")));
        adicionar(new Pedido("PED-1004", "LogisBrasil",
                List.of("VM Standard 4vCPU"),
                "ABERTO", new BigDecimal("690.00")));
        adicionar(new Pedido("PED-1005", "DataPrime",
                List.of("Kubernetes Gerenciado", "Backup Gerenciado"),
                "ABERTO", new BigDecimal("4120.00")));
        adicionar(new Pedido("PED-1006", "LogisBrasil",
                List.of("Object Storage 1TB", "CDN Global"),
                "FATURADO", new BigDecimal("1580.75")));
    }

    private void adicionar(Pedido pedido) {
        pedidos.put(pedido.id(), pedido);
    }

    /** Busca um pedido pelo id. Retorna {@code null} se não existir. */
    public Pedido porId(String id) {
        return pedidos.get(id);
    }

    /** Lista todos os pedidos de um cliente. Lista VAZIA se o cliente não tiver nenhum. */
    public List<Pedido> porCliente(String cliente) {
        return pedidos.values().stream()
                .filter(p -> p.cliente().equalsIgnoreCase(cliente))
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .collect(Collectors.toList());
    }

    /**
     * Cancela um pedido: substitui a entrada por uma cópia com status {@code CANCELADO}.
     *
     * <p>O {@code motivo} não é persistido num campo do {@link Pedido} (o record não tem
     * esse campo, de propósito, para manter o domínio enxuto): ele é apenas registrado no
     * log e devolvido na mensagem de confirmação da tool. Retorna o pedido já cancelado,
     * ou {@code null} se o id não existir.
     */
    public Pedido cancelar(String id, String motivo) {
        Pedido atual = pedidos.get(id);
        if (atual == null) {
            return null;
        }
        Pedido cancelado = new Pedido(atual.id(), atual.cliente(), atual.itens(),
                "CANCELADO", atual.valorTotal());
        pedidos.put(id, cancelado);
        return cancelado;
    }
}
