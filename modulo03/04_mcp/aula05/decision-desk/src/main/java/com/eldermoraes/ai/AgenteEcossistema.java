package com.eldermoraes.ai;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import io.quarkiverse.langchain4j.mcp.runtime.McpToolBox;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Agente que confronta a análise do repositório com a documentação do ecossistema.
 *
 * <p>A caixa dele é outra: {@code @McpToolBox("deepwiki")}. Ele só enxerga o DeepWiki,
 * jamais o filesystem do {@link AgenteRepo}. No log, compare os dois requests lado a lado:
 * a lista de tools deste agente traz {@code ask_question} (e afins do DeepWiki) e nada de
 * {@code read_file}/{@code write_file}. Duas listas de tools diferentes = menor privilégio
 * por agente, visível no terminal.
 */
@ApplicationScoped
@RegisterAiService
public interface AgenteEcossistema {

    @SystemMessage("""
            Você é um arquiteto de software. Você recebe a análise que outro agente fez do
            repositório local e a confronta com a documentação do ECOSSISTEMA para produzir
            uma recomendação fundamentada.

            Para consultar o ecossistema, use a tool ask_question (DeepWiki), fazendo perguntas
            a repositórios públicos relevantes ao tema — por exemplo quarkusio/quarkus ou
            langchain4j/langchain4j. Confronte o que a doc oficial diz com o que o repositório
            local mostrou; aponte convergências e divergências.

            Produza uma PROPOSTA DE MINI-ADR (Architecture Decision Record) em markdown, com
            exatamente estas seções:

            ## Contexto
            ## Decisão recomendada
            ## Alternativas consideradas
            ## Consequências

            Escreva em português (BR), objetivo e específico ao caso — sem encher linguiça.
            """)
    @UserMessage("""
            PERGUNTA: {pergunta}

            ANÁLISE DO REPOSITÓRIO:
            {analiseRepo}
            """)
    @Agent(name = "agenteEcossistema",
            description = "Confronta a análise do repositório com a documentação do ecossistema",
            outputKey = "visaoEcossistema")
    @McpToolBox("deepwiki")
    String consultarEcossistema(@V("pergunta") String pergunta, @V("analiseRepo") String analiseRepo);
}
