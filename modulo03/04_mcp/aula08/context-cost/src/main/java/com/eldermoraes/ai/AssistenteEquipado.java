package com.eldermoraes.ai;

import dev.langchain4j.service.SystemMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * O agente propositalmente superequipado.
 *
 * <p>Repare no que este código <b>não</b> tem: nenhum método {@code @Tool} local. Todas
 * as capacidades vêm de fora, de três servidores MCP plugados de forma declarativa no
 * {@code application.properties} ({@code quarkus.langchain4j.mcp.*}). É o contraste com a
 * aula 03, onde tudo foi montado à mão, em código (transporte + cliente + tool
 * provider). Aqui não há uma linha de código para ligar as peças: a anotação
 * {@link McpToolBox} abaixo é a única cola entre o agente e os servidores.
 *
 * <p><b>Por que superequipar de propósito?</b> Porque este projeto existe para medir o
 * custo do superequipamento, não para resolvê-lo. Três servidores despejam todas as suas
 * definições de tools em todo request ao modelo: é a falha 1 (tool-definition overload).
 * E qualquer resultado de tool (o conteúdo de um arquivo, por exemplo) passa pelo modelo
 * na ida e na volta: é a falha 2 (intermediate-result overload). Este agente é o
 * paciente; o log é o exame.
 */
@ApplicationScoped
@RegisterAiService
public interface AssistenteEquipado {

    /**
     * O {@link McpToolBox} declara quais servidores MCP este AI service enxerga, pelo nome
     * que cada um recebeu no {@code application.properties} ("filesystem", "deepwiki",
     * "context7"). Listamos os três explicitamente: é o mesmo mecanismo de "subset de
     * servidores por AI service" citado na aula. Encolher esta lista para um nome só é
     * exatamente o "cenário C" do README (menos servidores → tabela do boot menor →
     * request menor). Aqui, de propósito, mantemos os três para a conta doer.
     */
    @McpToolBox({ "filesystem", "deepwiki", "context7" })
    @SystemMessage("""
            Você é um assistente que responde usando as tools disponíveis, fornecidas por
            servidores MCP externos:
            - tools de FILESYSTEM (list_directory, read_file, write_file, ...) para ler e
              escrever arquivos no diretório de dados autorizado;
            - tools de servidores REMOTOS (DeepWiki e Context7) para consultar documentação
              de repositórios e bibliotecas.

            Regras:
            - Use as tools para obter fatos; não invente nomes de arquivos, caminhos ou
              conteúdo.
            - Se a pergunta não precisar de nenhuma tool (ex.: uma conta simples),
              responda direto.
            - Responda em português (BR), de forma objetiva.
            """)
    String perguntar(String pergunta);
}
