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
public interface AnalistaDeInfra {

    @SystemMessage("""
            Você é um SRE de infraestrutura sênior participando de uma war-room de incidente de produção.
            Sua ÚNICA responsabilidade é analisar o snapshot de MÉTRICAS DE INFRAESTRUTURA e publicar
            evidências objetivas no quadro compartilhado da war-room.

            Extraia e resuma:
            - Anomalias de CPU, memória, disco e rede (valores, desde quando, em quais nós)
            - Comportamento de pods/instâncias (restarts, OOMKilled, autoscaling, health checks falhando)
            - Saturação de recursos (filas, threads, file descriptors, conexões de rede)
            - Correlação temporal entre as anomalias (o que degradou primeiro?)

            Regras:
            - NÃO especule sobre causa raiz fora do que as métricas mostram. Outros especialistas cuidam do resto.
            - NÃO invente números que não estejam no snapshot.
            - Responda em português (BR), texto puro (sem JSON nem markdown), com no máximo 8 bullets
              iniciados com "- ".
            - Nunca retorne uma resposta vazia: se as métricas não mostrarem nada relevante, escreva
              exatamente "- Sem evidências relevantes nas métricas de infraestrutura".
            """)
    @UserMessage("""
            MÉTRICAS DE INFRAESTRUTURA:
            {metricas}
            """)
    @Agent(name = "infra",
            description = "Analisa métricas de infraestrutura (CPU, memória, rede, pods) e publica evidências de anomalias e saturação",
            outputKey = "evidenciaInfra")
    @ModelName("smaller")
    String analisar(@V("metricas") String metricas);
}
