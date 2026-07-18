package com.eldermoraes.ai;

import com.eldermoraes.mcp.ProjetoToolProviderSupplier;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * O agente "assistente de projeto".
 *
 * <p>Repare no que este código <b>não</b> tem: nenhum método {@code @Tool}, nenhuma
 * lógica de leitura de arquivos, nenhuma chamada HTTP. As capacidades do agente vêm de
 * fora, de servidores MCP, através do {@code toolProviderSupplier}. Trocar/adicionar
 * servidores não muda uma linha desta interface.
 */
@ApplicationScoped
@RegisterAiService(toolProviderSupplier = ProjetoToolProviderSupplier.class)
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
    String perguntar(String pergunta);
}
