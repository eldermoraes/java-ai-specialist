package com.eldermoraes.ai;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * O agente "repo copilot": o mesmo assistente da aula03, reconstruído do jeito
 * declarativo do Quarkus.
 *
 * <p>Compare com a aula03: lá o {@code @RegisterAiService} recebia um
 * {@code toolProviderSupplier = ProjetoToolProviderSupplier.class}, e existiam duas
 * classes só para ligar as peças (McpClients + ProjetoToolProviderSupplier), montando
 * transporte, cliente e provider à mão. Aqui nada disso existe: não há
 * {@code toolProviderSupplier}, não há classe para ligar as peças. A extensão MCP cria
 * os clients a partir do
 * {@code application.properties} (quarkus.langchain4j.mcp.*), e este agente só declara,
 * por método, quais clients quer usar, via {@link McpToolBox}.
 *
 * <p>Detalhe importante: {@code @McpToolBox} opera por método. Este método pede as tools
 * dos clients "filesystem" e "deepwiki". Um outro método poderia pedir só
 * {@code @McpToolBox("filesystem")} e teria menos tools no request. E menos tools no
 * request significa prompt mais enxuto, ou seja, menos token gasto por chamada.
 *
 * <p>Assim como na aula03: nenhum método {@code @Tool}, nenhuma lógica de leitura de
 * arquivos. A diferença é que agora nem a montagem é sua: é da extensão. Sobra só a
 * assinatura e o toolbox nomeado.
 */
@ApplicationScoped
@RegisterAiService
public interface AssistenteProjeto {

    @SystemMessage("""
            Você é um assistente de projeto: ajuda o desenvolvedor a entender os arquivos
            de um projeto ou repositório.

            Você NÃO tem os arquivos embutidos no seu código. Para responder, use as tools
            disponíveis (fornecidas por servidores MCP):
            - tools de FILESYSTEM (list_directory, read_file, directory_tree, ...) para
              inspecionar o projeto LOCAL do desenvolvedor;
            - tools do servidor REMOTO (ex.: DeepWiki: ask_question, read_wiki_structure)
              para consultar a documentação de repositórios públicos do GitHub.

            Regras:
            - SEMPRE use as tools para obter fatos. Nunca invente nomes de arquivos,
              caminhos ou conteúdo.
            - Ao afirmar algo sobre um arquivo, cite o caminho dele.
            - Se a pergunta for sobre um repositório público do GitHub, prefira as tools
              remotas; se for sobre o projeto local, prefira as de filesystem.
            - Responda em português (BR), de forma objetiva.
            """)
    @McpToolBox({"filesystem", "deepwiki"})
    String perguntar(String pergunta);
}
