package com.eldermoraes.ai;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * O agente que consome a Central de Pedidos: o outro lado do fio do order-hub-live-server.
 *
 * <p>Repare no que este código não tem: nenhum método {@code @Tool} local, nenhuma lógica
 * de pedido. As capacidades chegam de fora, do MCP server, via {@link McpToolBox}. É o
 * arco do bloco se fechando: você construiu o server e agora consome o seu próprio server,
 * pelo mesmo caminho declarativo com que consumiria o de qualquer um.
 *
 * <p>{@code @McpToolBox("central-pedidos")} liga este método ao client MCP declarativo
 * configurado no {@code application.properties} sob a chave
 * {@code quarkus.langchain4j.mcp.central-pedidos.*}. A cada pergunta, o LangChain4j lista as
 * tools do server (buscar_pedido, listar_pedidos_por_cliente, cancelar_pedido) e as oferece
 * ao modelo.
 */
@ApplicationScoped
@RegisterAiService
public interface AssistentePedidos {

    @SystemMessage("""
            Você é o assistente da Central de Pedidos da Cloud For You, uma empresa B2B de
            serviços de nuvem. Você ajuda o time de atendimento a consultar e gerenciar
            pedidos.

            Você NÃO tem os pedidos embutidos no seu código. Para responder, use SEMPRE as
            tools disponíveis (fornecidas por um servidor MCP, a Central de Pedidos):
            - buscar_pedido: consulta UM pedido pelo identificador (ex.: PED-1001);
            - listar_pedidos_por_cliente: lista os pedidos de um cliente (ex.: TechNova);
            - cancelar_pedido: cancela um pedido — operação destrutiva.

            Regras:
            - Nunca invente identificadores, clientes, itens, status ou valores: obtenha os
              fatos pelas tools.
            - Cancelamento é sensível: a confirmação é feita PELO PROTOCOLO (elicitation) —
              não peça senha nem dado sensível, apenas acione a tool e deixe o protocolo
              conduzir a confirmação.
            - Responda em português (BR), de forma objetiva e cordial.
            """)
    @McpToolBox("central-pedidos")
    String perguntar(String pergunta);
}
