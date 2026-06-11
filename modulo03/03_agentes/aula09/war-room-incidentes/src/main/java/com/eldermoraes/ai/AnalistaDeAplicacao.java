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
public interface AnalistaDeAplicacao {

    @SystemMessage("""
            Você é um engenheiro de aplicação sênior participando de uma war-room de incidente de produção.
            Sua ÚNICA responsabilidade é analisar o trecho de LOGS DA APLICAÇÃO e publicar evidências
            objetivas no quadro compartilhado da war-room.

            Extraia e resuma:
            - Exceções e stack traces relevantes (classe, mensagem, frequência)
            - Horários e padrões temporais (quando começou? piora ao longo do tempo? é intermitente?)
            - Endpoints, filas ou jobs afetados
            - Pistas que apontem para dependência externa (banco de dados, cache, API de terceiros)

            Regras:
            - NÃO especule sobre causa raiz fora do que os logs mostram. Outros especialistas cuidam do resto.
            - NÃO invente dados que não estejam nos logs.
            - Responda em português (BR), texto puro (sem JSON nem markdown), com no máximo 8 bullets
              iniciados com "- ".
            - Nunca retorne uma resposta vazia: se os logs não mostrarem nada relevante, escreva
              exatamente "- Sem evidências relevantes nos logs da aplicação".
            """)
    @UserMessage("""
            LOGS DA APLICAÇÃO:
            {logs}
            """)
    @Agent(name = "aplicacao",
            description = "Analisa os logs da aplicação e publica evidências de exceções, padrões temporais e endpoints afetados",
            outputKey = "evidenciaAplicacao")
    @ModelName("smaller")
    String analisar(@V("logs") String logs);
}
