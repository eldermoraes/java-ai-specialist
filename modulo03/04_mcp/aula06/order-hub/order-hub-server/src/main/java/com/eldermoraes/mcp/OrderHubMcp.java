package com.eldermoraes.mcp;

import java.util.List;

import org.jboss.logging.Logger;

import com.eldermoraes.dominio.PedidoRepository;
import com.eldermoraes.dto.Pedido;
import com.eldermoraes.dto.PedidosDoCliente;

import io.quarkiverse.mcp.server.Elicitation;
import io.quarkiverse.mcp.server.ElicitationRequest;
import io.quarkiverse.mcp.server.ElicitationResponse;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.inject.Inject;

/**
 * O MCP server da Central de Pedidos: a classe que expõe três tools pelo protocolo.
 *
 * <p><b>Cuidado com o nome da anotação!</b> Este {@code @Tool} é o
 * {@code io.quarkiverse.mcp.server.Tool}, o do lado de quem expõe. Ele não é o
 * {@code dev.langchain4j.agent.tool.Tool} que você usou nos agentes (o do lado de quem
 * consome). Mesma palavra, bibliotecas diferentes, lados opostos do fio. É fácil importar
 * a errada e passar meia hora sem entender por que nada funciona: confira o import lá em
 * cima.
 *
 * <p>Repare também que os métodos Java são camelCase ({@code buscarPedido}), mas o nome
 * exposto pelo protocolo, no atributo {@code name}, é snake_case ({@code buscar_pedido}):
 * a convenção de fato do ecossistema MCP entre clients.
 */
public class OrderHubMcp {

    private static final Logger LOG = Logger.getLogger(OrderHubMcp.class);

    @Inject
    PedidoRepository repositorio;

    /**
     * A descrição da tool é a interface para o modelo: quem a lê é o LLM do outro lado do
     * fio, não um humano. Uma descrição vaga produz chamadas erradas; uma precisa produz
     * chamadas certas. Capriche nela como na assinatura de um método público.
     *
     * <p>{@code structuredContent=true} + retorno de um record = a extensão serializa o
     * {@link Pedido} como {@code structuredContent} na resposta. As annotations descrevem o
     * comportamento de forma declarativa: esta tool só lê ({@code readOnlyHint=true}), não
     * destrói nada ({@code destructiveHint=false}) e chamá-la N vezes tem o mesmo efeito
     * ({@code idempotentHint=true}).
     */
    @Tool(name = "buscar_pedido",
            description = """
                    Busca um pedido da Central de Pedidos pelo seu identificador e retorna
                    seus dados estruturados: cliente, itens contratados, status atual e valor
                    total. Use quando o usuário quiser consultar UM pedido específico e você
                    tiver o identificador dele (ex.: PED-1001).""",
            structuredContent = true,
            annotations = @Tool.Annotations(
                    title = "Buscar pedido",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true))
    public Pedido buscarPedido(
            @ToolArg(description = "O identificador do pedido, ex.: PED-1001") String id) {
        Pedido pedido = repositorio.porId(id);
        if (pedido == null) {
            // Erro de negócio, não falha de transporte. A ToolCallException vira uma
            // resposta de tool com isError=true (não um HTTP 500), e o modelo lê esse erro e
            // se recupera (tenta outro id, avisa o usuário).
            throw new ToolCallException("Pedido não encontrado: " + id);
        }
        return pedido;
    }

    /**
     * Lista todos os pedidos de um cliente. Também é read-only. Devolve o wrapper
     * {@link PedidosDoCliente} (o topo do structuredContent é sempre um objeto). Cliente sem
     * pedidos não é erro: retorna uma lista vazia, e o modelo interpreta isso naturalmente.
     */
    @Tool(name = "listar_pedidos_por_cliente",
            description = """
                    Lista todos os pedidos de um cliente da Central de Pedidos, pelo nome do
                    cliente. Use quando o usuário quiser ver o histórico ou o conjunto de
                    pedidos de um cliente (ex.: TechNova). Se o cliente não tiver pedidos,
                    retorna uma lista vazia.""",
            structuredContent = true,
            annotations = @Tool.Annotations(
                    title = "Listar pedidos por cliente",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true))
    public PedidosDoCliente listarPedidosPorCliente(
            @ToolArg(description = "O nome do cliente, ex.: TechNova") String cliente) {
        List<Pedido> pedidos = repositorio.porCliente(cliente);
        return new PedidosDoCliente(cliente, pedidos);
    }

    /**
     * Cancela um pedido: operação destrutiva, e é aqui que mora o superpoder desta aula.
     *
     * <p>Duas metades de "operações destrutivas nunca autônomas", ambas trazidas para dentro
     * do protocolo: a annotation {@code destructiveHint=true} avisa (o client pode destacar a
     * tool na UI ou exigir aprovação), e a <b>elicitation</b> confirma no momento da chamada.
     * Elicitation é o human-in-the-loop dos agentes promovido a primitiva de protocolo: o
     * server pausa no meio da chamada, pergunta ao usuário e só age com a resposta.
     *
     * <p>Guardrail da spec: elicitation é para confirmação e contexto, nunca para credencial
     * (senha, token, cartão). Aqui pedimos só a confirmação e, se faltar, o motivo.
     */
    @Tool(name = "cancelar_pedido",
            description = """
                    Cancela um pedido da Central de Pedidos. Operação DESTRUTIVA: antes de
                    cancelar, o server pede a confirmação do usuário pelo protocolo
                    (elicitation) e, se o motivo não vier no argumento, pergunta o motivo.
                    Use quando o usuário pedir explicitamente para cancelar um pedido.""",
            annotations = @Tool.Annotations(
                    title = "Cancelar pedido",
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = false))
    public String cancelarPedido(
            @ToolArg(description = "O identificador do pedido a cancelar, ex.: PED-1003")
            String id,
            @ToolArg(description = "O motivo do cancelamento (opcional; se ausente, será "
                    + "solicitado na confirmação)", required = false) String motivo,
            Elicitation elicitation) {

        // 1) O pedido existe? Erro de negócio se não existir (isError, não HTTP 500).
        Pedido pedido = repositorio.porId(id);
        if (pedido == null) {
            throw new ToolCallException("Pedido não encontrado: " + id);
        }

        // 2) Elicitation depende de negociação: o client precisa ter declarado a capability
        //    no handshake. Se ele não suporta (ou não suporta o form mode que usamos aqui),
        //    nunca execute a operação destrutiva assumindo uma confirmação que não veio:
        //    recuse com uma mensagem clara.
        //    (Para clients stateless a extensão documenta o padrão MRTR, a confirmação em
        //    duas fases via nova chamada com as respostas de elicitation, fora do escopo
        //    desta aula; aqui a recusa segura basta para demonstrar o guardrail.)
        if (!elicitation.isSupported() || !elicitation.isFormModeSupported()) {
            LOG.warnf("Cancelamento de %s recusado: client sem suporte a elicitation.", id);
            return "Este client não suporta confirmação interativa (elicitation), então o "
                    + "pedido " + id + " NÃO foi cancelado. Cancelar é uma operação destrutiva "
                    + "e exige confirmação do usuário.";
        }

        // 3) Monta a elicitation: mensagem de confirmação + (se o motivo não veio no arg) uma
        //    propriedade "motivo" no schema, marcada como obrigatória.
        boolean motivoAusente = (motivo == null || motivo.isBlank());
        ElicitationRequest.Builder builder = elicitation.requestBuilder()
                .setMessage("Confirma o cancelamento do pedido " + pedido.id()
                        + " (cliente " + pedido.cliente()
                        + ", valor " + pedido.valorTotal() + ")?");
        builder.addSchemaProperty("motivo", new ElicitationRequest.StringSchema.StringSchemaBuilder()
                .setTitle("Motivo do cancelamento")
                .setDescription("Descreva por que o pedido está sendo cancelado.")
                .setRequired(motivoAusente)
                .build());

        // 4) sendAndAwait: pausa a chamada, pergunta, espera a resposta do usuário.
        ElicitationResponse resposta = builder.build().sendAndAwait();

        // 5) actionAccepted() = o usuário confirmou (ACCEPT). DECLINE/CANCEL = abortou.
        if (!resposta.actionAccepted()) {
            LOG.infof("Cancelamento de %s abortado pelo usuário.", id);
            return "Cancelamento abortado pelo usuário.";
        }

        String motivoFinal = motivoAusente ? resposta.content().getString("motivo") : motivo;
        repositorio.cancelar(id, motivoFinal);
        LOG.infof("Pedido %s cancelado. Motivo: %s", id, motivoFinal);
        return "Pedido " + id + " cancelado. Motivo: " + motivoFinal;
    }
}
