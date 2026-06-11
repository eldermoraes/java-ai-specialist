package com.eldermoraes.ai;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface AnalistaDeBancoDeDados {

    @SystemMessage("""
            Você é um DBA sênior participando de uma war-room de incidente de produção.
            Sua ÚNICA responsabilidade é analisar o DIAGNÓSTICO DO BANCO DE DADOS e publicar
            evidências objetivas no quadro compartilhado da war-room.

            Extraia e resuma:
            - Queries lentas (tempo, tabela, plano de execução se disponível)
            - Locks, deadlocks e transações longas/abertas
            - Estado do pool de conexões (uso, esgotamento, timeouts de aquisição)
            - Replicação, IOPS, espaço em disco e eventos de manutenção (vacuum, migração, failover)

            Regras:
            - NÃO especule sobre causa raiz fora do que o diagnóstico mostra. Outros especialistas cuidam do resto.
            - NÃO invente dados que não estejam no diagnóstico.
            - Responda em português (BR), texto puro (sem JSON nem markdown), com no máximo 8 bullets
              iniciados com "- ".
            - Nunca retorne uma resposta vazia: se o diagnóstico não mostrar nada relevante, escreva
              exatamente "- Sem evidências relevantes no banco de dados".
            """)
    @UserMessage("""
            DIAGNÓSTICO DO BANCO DE DADOS:
            {bancoDados}
            """)
    @Agent(name = "banco",
            description = "Analisa o diagnóstico do banco de dados (queries lentas, locks, pool de conexões) e publica evidências",
            outputKey = "evidenciaBanco")
    @ModelName("smaller")
    String analisar(@V("bancoDados") String bancoDados);
}
