package com.eldermoraes.ai;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Agente que grava o mini-ADR aprovado no disco.
 *
 * <p>É a mesma caixa {@code "filesystem"} do {@link AgenteRepo}: quem lê o repositório é
 * quem segura a caneta, porque leitura e escrita chegam juntas no mesmo server MCP. O grão
 * do {@code @McpToolBox} é o server, não a tool. Não há um segundo server só de escrita a
 * quem delegar o risco. É exatamente por isso que o gate humano vem antes deste agente:
 * o {@code write_file} de um server de terceiros só acontece depois do "sim" do humano.
 */
@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface EscritorAdr {

    @SystemMessage("""
            Você é um registrador de decisões de engenharia. Você recebe um mini-ADR já
            APROVADO por um humano e precisa gravá-lo em disco usando as tools do filesystem.

            Siga esta sequência, sempre com as tools (nunca invente caminhos):
            1. Chame list_allowed_directories para descobrir o diretório autorizado.
            2. Chame create_directory para garantir a subpasta "decisions/" dentro dele.
            3. Chame write_file para gravar o arquivo
               "decisions/adr-AAAAMMDD-<slug-curto-da-pergunta>.md" (AAAAMMDD = data de hoje;
               slug = 3 a 5 palavras da pergunta, minúsculas, separadas por hífen), com o
               conteúdo integral do mini-ADR recebido.

            Regras:
            - NUNCA grave fora da subpasta "decisions/".
            - Não altere o conteúdo do mini-ADR; apenas grave-o.
            - Ao terminar, responda em português (BR) confirmando o CAMINHO exato do arquivo gravado.
            """)
    @UserMessage("""
            PERGUNTA ORIGINAL: {pergunta}

            MINI-ADR APROVADO (gravar exatamente este conteúdo):
            {visaoEcossistema}
            """)
    @Agent(name = "escritorAdr",
            description = "Grava o mini-ADR aprovado na subpasta decisions/ do repositório",
            outputKey = "registro")
    @McpToolBox("filesystem")
    String registrar(@V("pergunta") String pergunta, @V("visaoEcossistema") String visaoEcossistema);
}
