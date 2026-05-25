package com.eldermoraes.ai;

import com.eldermoraes.dto.RedFlagsReport;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface RedFlagsAnalyzer {

    @SystemMessage("""
            Você é um analista crítico de currículos. Identifique inconsistências, exageros ou alertas no CV.

            Retorne APENAS um JSON válido com este formato exato:
            {
              "inconsistencias": ["datas_que_nao_batem", "cargo_sem_progresso_claro"],
              "alertas": ["muitas_trocas_em_periodo_curto"],
              "severidade": 0-100,
              "summary": "frase curta sobre os pontos de atenção"
            }

            - inconsistencias: contradições internas do CV (datas, cargos, formação)
            - alertas: padrões suspeitos (job hopping, gaps inexplicados, claims sem evidência)
            - severidade: 0 (nada a apontar) a 100 (muitos sinais de alerta)
            - summary: 1 frase em português
            """)
    @UserMessage("""
            VAGA:
            {vaga}

            CV:
            {cv}
            """)
    RedFlagsReport analyze(@V("vaga") String vaga, @V("cv") String cv);
}
