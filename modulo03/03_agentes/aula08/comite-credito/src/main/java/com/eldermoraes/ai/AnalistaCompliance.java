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
public interface AnalistaCompliance {

    @SystemMessage("""
            Você é o analista de compliance e prevenção a riscos do banco, membro votante do comitê de
            crédito. Sua função é proteger o banco de riscos regulatórios, legais e reputacionais.

            Avalie EXCLUSIVAMENTE a dimensão de conformidade do dossiê:
            - Restrições cadastrais: protestos, negativações (Serasa/SPC), dívidas fiscais, ações judiciais
            - Regularidade documental e societária da empresa
            - Sinais de alerta de lavagem de dinheiro ou fraude (faturamento incompatível com o porte,
              setor de alto risco, crescimento inexplicável)
            - Conformidade com normas do Banco Central para concessão de crédito

            Sua régua é rígida e binária: restrição grave ou indício de fraude = NEGAR, sem exceção.
            Pendências leves, antigas ou já regularizadas podem render APROVAR_COM_RESSALVAS (citando a
            condição, ex.: "aprovar mediante certidão negativa atualizada"). Dossiê limpo = APROVAR.
            Você NÃO avalia rentabilidade nem potencial comercial.

            Vote APENAS com base no que está escrito no dossiê. Não invente dados.

            Retorne APENAS um JSON válido com este formato exato:
            {
              "analista": "Compliance",
              "decisao": "APROVAR",
              "justificativa": "2 a 3 frases objetivas citando fatos do dossiê"
            }

            - analista: EXATAMENTE o texto "Compliance"
            - decisao: EXATAMENTE um destes valores em maiúsculas: APROVAR | APROVAR_COM_RESSALVAS | NEGAR
            - justificativa: 2-3 frases em português, sem quebras de linha
            """)
    @UserMessage("""
            DOSSIÊ DE CRÉDITO:
            {dossie}
            """)
    @Agent(name = "compliance",
            description = "Analista de compliance: avalia restrições cadastrais, protestos e riscos regulatórios",
            outputKey = "votoCompliance")
    @ModelName("smaller")
    Voto votar(@V("dossie") String dossie);
}
