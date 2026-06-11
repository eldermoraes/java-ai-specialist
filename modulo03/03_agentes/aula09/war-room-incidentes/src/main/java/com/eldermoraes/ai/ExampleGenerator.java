package com.eldermoraes.ai;

import com.eldermoraes.dto.Incidente;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface ExampleGenerator {

    @SystemMessage("""
            Você é gerador de cenários de incidente de produção para uso em demos de war-room.
            Crie um incidente realista de um sistema corporativo brasileiro (e-commerce, banco digital,
            logística, ERP ou app de delivery).

            Varie aleatoriamente o cenário entre:
            - Esgotamento do pool de conexões com o banco (transações longas ou vazamento de conexões)
            - Memory leak introduzido por deploy recente (heap crescendo até OOMKilled)
            - Índice ausente após migração de schema (queries lentas + CPU alta no banco)
            - Disco cheio no nó do banco de dados (escritas falhando)
            - Latência de rede entre zonas de disponibilidade (timeouts intermitentes)
            - Certificado TLS expirado em integração interna (falhas em cascata)
            - Cache invalidado em massa (thundering herd no banco)

            REGRA MAIS IMPORTANTE: os quatro campos devem contar a MESMA história de forma consistente
            entre si — os logs, as métricas e o diagnóstico do banco precisam apontar, juntos, para a
            mesma causa raiz do cenário escolhido (com algum ruído realista de sinais irrelevantes).

            Conteúdo de cada campo (tudo em português BR; \\n para quebras de linha dentro das strings):
            - "sintoma": 1-2 frases, como um alerta de monitoramento (ex.: "Alerta P1: checkout com
              erro 500 em 35% das requisições desde 14h12; suspeita de esgotamento do pool de conexões").
            - "logs": 5 a 10 linhas de log realistas (timestamp, nível, classe Java, mensagem;
              inclua stack trace resumido quando fizer sentido).
            - "metricas": 5 a 8 linhas no estilo dashboard (ex.: "CPU api-pagamentos: 45% -> 92% desde 14h10").
            - "bancoDados": 5 a 8 linhas de diagnóstico de banco (ex.: "pg_stat_activity: 198/200 conexões
              ativas, 37 em 'idle in transaction' há mais de 10min").

            Retorne APENAS um JSON válido neste formato exato:
            {
              "sintoma": "...",
              "logs": "linha1\\nlinha2\\n...",
              "metricas": "linha1\\nlinha2\\n...",
              "bancoDados": "linha1\\nlinha2\\n..."
            }
            """)
    @UserMessage("Gere um novo incidente de exemplo agora.")
    Incidente incidenteExemplo();
}
