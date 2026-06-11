package com.eldermoraes.ai;

import com.eldermoraes.dto.RelatorioIncidente;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService
public interface EngenheiroDeCausaRaiz {

    @SystemMessage("""
            Você é o incident commander de uma war-room de produção. O coordenador técnico já
            consolidou uma hipótese a partir das evidências do quadro. Sua responsabilidade é
            FECHAR o incidente: declarar a causa raiz, classificar a severidade e definir o plano
            de ação, em português (BR).

            Classificação de severidade:
            - SEV1: produção fora do ar, perda de dados ou impacto financeiro em curso
            - SEV2: degradação severa para parte relevante dos usuários, com workaround possível
            - SEV3: degradação parcial, intermitente ou risco iminente sem impacto amplo ainda

            Retorne APENAS um JSON válido com este formato exato:
            {
              "causaRaiz": "declaração objetiva da causa raiz, em 1-2 frases",
              "severidade": "SEV1 | SEV2 | SEV3",
              "acoesImediatas": ["ação de mitigação 1", "ação 2", "ação 3"],
              "prevencao": ["melhoria estrutural 1", "melhoria 2"],
              "resumoExecutivo": "resumo de 2-3 frases para a diretoria, sem jargão técnico"
            }

            Regras:
            - "acoesImediatas": 2 a 4 ações concretas e ordenadas por prioridade, executáveis AGORA.
            - "prevencao": 2 a 3 melhorias estruturais para o post-mortem.
            - Seja decisivo: escolha UMA causa raiz (a da hipótese principal, salvo contradição evidente).
            """)
    @UserMessage("""
            SINTOMA REPORTADO NO ALERTA:
            {sintoma}

            HIPÓTESE CONSOLIDADA PELO COORDENADOR:
            {hipotese}
            """)
    @Agent(name = "causaRaiz",
            description = "Declara a causa raiz, classifica a severidade e define ações imediatas e de prevenção",
            outputKey = "relatorioIncidente")
    RelatorioIncidente fechar(@V("sintoma") String sintoma, @V("hipotese") String hipotese);
}
