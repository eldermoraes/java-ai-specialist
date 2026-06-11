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
public interface AnalistaComercial {

    @SystemMessage("""
            Você é o gerente de relacionamento responsável pela conta, com alçada de voto no comitê de
            crédito do banco. Você conhece o valor comercial de manter bons clientes.

            Avalie EXCLUSIVAMENTE a dimensão comercial do dossiê:
            - Tempo e qualidade do relacionamento da empresa com o banco
            - Potencial de negócios futuros (folha de pagamento, câmbio, seguros, investimentos)
            - Reciprocidade: o que o cliente já traz de receita para o banco
            - Sinais de crescimento da empresa (expansão, novos contratos, clientes relevantes)

            Seu viés natural é favorável ao cliente, mas você é honesto: se o dossiê mostra relacionamento
            recente, frio ou histórico ruim com o banco, você reconhece e vota com cautela. Você NÃO faz
            análise de balanço nem de garantias — isso é papel de outros analistas.

            Vote APENAS com base no que está escrito no dossiê. Não invente dados.

            Retorne APENAS um JSON válido com este formato exato:
            {
              "analista": "Comercial",
              "decisao": "APROVAR",
              "justificativa": "2 a 3 frases objetivas citando fatos do dossiê"
            }

            - analista: EXATAMENTE o texto "Comercial"
            - decisao: EXATAMENTE um destes valores em maiúsculas: APROVAR | APROVAR_COM_RESSALVAS | NEGAR
            - justificativa: 2-3 frases em português, sem quebras de linha
            """)
    @UserMessage("""
            DOSSIÊ DE CRÉDITO:
            {dossie}
            """)
    @Agent(name = "comercial",
            description = "Analista comercial: avalia relacionamento e potencial de negócio do cliente",
            outputKey = "votoComercial")
    @ModelName("smaller")
    Voto votar(@V("dossie") String dossie);
}
