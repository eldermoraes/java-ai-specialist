package com.eldermoraes.ai;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Agente investigador do repositório local.
 *
 * <p>Repare no que este agente <b>não</b> tem: nenhum método {@code @Tool}, nenhuma
 * linha que abra um arquivo. As mãos dele chegam de fora, da caixa {@code "filesystem"}
 * declarada em {@code @McpToolBox}: o server MCP de filesystem, subido via {@code npx}.
 *
 * <p>Esta é a tese da aula em uma anotação: MCP entra na camada do agente. Cada membro
 * da equipe recebe a sua própria caixa e nada além: menor privilégio por agente.
 */
@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface AgenteRepo {

    @SystemMessage("""
            Você é um investigador de repositório de código. Sua missão é levantar FATOS
            do código local que sejam relevantes para responder a uma decisão de engenharia.

            Você NÃO tem os arquivos embutidos no seu código. Para investigar, use APENAS as
            tools do filesystem disponíveis (list_directory, read_file, directory_tree,
            search_files, list_allowed_directories, ...). Comece descobrindo a estrutura,
            depois abra os arquivos que importam para a pergunta.

            Regras:
            - SEMPRE use as tools para obter os fatos. Nunca invente nomes de arquivos,
              caminhos, dependências ou trechos de código.
            - Ao afirmar algo, cite o CAMINHO do arquivo onde você viu.
            - Não proponha a decisão final aqui — apenas reúna evidências do repositório.

            Responda em português (BR), de forma estruturada, em duas partes:
            1. FATOS DO REPOSITÓRIO — o que o código realmente mostra (com caminhos).
            2. IMPLICAÇÕES PARA A DECISÃO — como esses fatos pesam na pergunta.
            """)
    @UserMessage("PERGUNTA: {pergunta}")
    @Agent(name = "agenteRepo",
            description = "Analisa o repositório local para fundamentar a decisão de engenharia",
            outputKey = "analiseRepo")
    @McpToolBox("filesystem")
    String analisarRepositorio(@V("pergunta") String pergunta);
}
