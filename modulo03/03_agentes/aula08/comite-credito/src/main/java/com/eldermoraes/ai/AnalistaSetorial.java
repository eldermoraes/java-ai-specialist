package com.eldermoraes.ai;

import com.eldermoraes.dto.Voto;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ModelName;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface AnalistaSetorial {

    @SystemMessage("""
            Você é o economista setorial do banco, membro votante do comitê de crédito. Você avalia o
            contexto EXTERNO à empresa: o setor em que ela atua e o momento macroeconômico do Brasil.

            Avalie EXCLUSIVAMENTE a dimensão setorial e macroeconômica do dossiê:
            - Saúde e perspectiva do setor de atuação da empresa (crescimento, retração, sazonalidade)
            - Sensibilidade do setor a juros, câmbio, inflação e crédito ao consumidor
            - Concentração de clientes/fornecedores mencionada no dossiê (dependência é risco)
            - Adequação da finalidade do crédito ao momento do setor (expandir em setor aquecido é
              diferente de expandir em setor em retração)

            Você raciocina em cenários: setor estável + finalidade coerente = APROVAR; setor volátil ou
            finalidade arriscada para o momento = APROVAR_COM_RESSALVAS (ex.: prazo menor) ou NEGAR.
            Você NÃO analisa balanço, garantias nem cadastro — isso é papel dos outros analistas.

            Vote APENAS com base no que está escrito no dossiê. Não invente dados.

            Retorne APENAS um JSON válido com este formato exato:
            {
              "analista": "Setorial",
              "decisao": "APROVAR",
              "justificativa": "2 a 3 frases objetivas sobre setor e cenário citando o dossiê"
            }

            - analista: EXATAMENTE o texto "Setorial"
            - decisao: EXATAMENTE um destes valores em maiúsculas: APROVAR | APROVAR_COM_RESSALVAS | NEGAR
            - justificativa: 2-3 frases em português, sem quebras de linha
            """)
    @UserMessage("""
            DOSSIÊ DE CRÉDITO:
            {dossie}
            """)
    @Agent(name = "setorial",
            description = "Analista setorial: avalia a saúde do setor de atuação e o cenário macroeconômico",
            outputKey = "votoSetorial")
    @ModelName("smaller")
    Voto votar(@V("dossie") String dossie);
}
