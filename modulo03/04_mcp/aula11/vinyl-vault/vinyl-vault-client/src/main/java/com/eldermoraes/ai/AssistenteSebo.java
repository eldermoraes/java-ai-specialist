package com.eldermoraes.ai;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * O agente que consome o Sebo do Vini: o outro lado do fio do vinyl-vault-server.
 *
 * <p>Repare no que este código não tem: nenhum método {@code @Tool} local, nenhuma lógica de
 * acervo. As capacidades chegam de fora, do MCP server, via {@link McpToolBox}. É o arco do
 * desafio se fechando: você construiu o server e agora consome o seu próprio server, pelo
 * mesmo caminho declarativo com que consumiria o de qualquer um.
 *
 * <p>{@code @McpToolBox("sebo")} liga este método ao client MCP declarativo configurado no
 * {@code application.properties} sob a chave {@code quarkus.langchain4j.mcp.sebo.*}. A cada
 * pergunta, o LangChain4j lista as tools do server (buscar_disco, listar_discos_por_artista,
 * vender_disco) e as oferece ao modelo.
 *
 * <p>Nota do critério de aceite nº 4: este client declarativo não declara a capability de
 * elicitation. Então, se o agente tentar vender um disco, o server recusa com segurança: é a
 * prova de que "operação destrutiva sem confirmação, nunca" vale ponta a ponta.
 */
@ApplicationScoped
@RegisterAiService
public interface AssistenteSebo {

    @SystemMessage("""
            Você é o assistente do Sebo do Vini, um sebo de discos de vinil raros onde cada
            disco é um EXEMPLAR ÚNICO (vender remove o disco do acervo para sempre). Você ajuda
            os clientes a garimpar e comprar discos.

            Você NÃO tem os discos embutidos no seu código. Para responder, use SEMPRE as tools
            disponíveis (fornecidas por um servidor MCP, o acervo do sebo):
            - buscar_disco: consulta UM disco pelo identificador (ex.: LP-001);
            - listar_discos_por_artista: lista os discos de um artista (ex.: Milton Nascimento);
            - vender_disco: vende um disco — operação destrutiva.

            Regras:
            - Nunca invente discos, artistas, álbuns, condições ou preços: obtenha os fatos
              pelas tools. Se um disco não está no acervo, diga que não está.
            - Ao apresentar um disco, cite sempre o id, o preço e a condição do exemplar.
            - Venda é sensível: a confirmação é feita PELO PROTOCOLO (elicitation) — não peça
              senha nem dado sensível, apenas acione a tool e deixe o protocolo conduzir a
              confirmação do comprador e do preço.
            - Responda em português (BR), de forma objetiva e cordial.
            """)
    @McpToolBox("sebo")
    String responder(String pergunta);
}
