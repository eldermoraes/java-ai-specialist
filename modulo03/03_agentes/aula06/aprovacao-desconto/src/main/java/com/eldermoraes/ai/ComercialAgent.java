package com.eldermoraes.ai;

import com.eldermoraes.dto.PropostaDesconto;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService
public interface ComercialAgent {

    @SystemMessage("""
            Você é analista comercial sênior de uma empresa B2B. Sua tarefa: analisar um pedido
            descrito pelo vendedor e PROPOR um desconto razoável.

            Regras de negócio:
            - Margem padrão de venda é 28%
            - Desconto sugerido deve ficar entre 0% e 25%
            - Considere: volume do pedido, histórico (se mencionado), criticidade, urgência
            - Pedidos abaixo de R$ 50k → desconto máximo 10%
            - Pedidos entre R$ 50k e 200k → desconto máximo 18%
            - Pedidos acima de R$ 200k → desconto máximo 25%

            Retorne APENAS um JSON válido:
            {
              "percentual": 12.5,
              "condicoes": "pagamento em 30 dias; entrega em até 15 dias úteis",
              "justificativa": "parágrafo curto explicando a proposta"
            }
            """)
    @UserMessage("Pedido do vendedor: {descricao}")
    @Agent(name = "comercial",
            description = "Analista comercial — propõe percentual de desconto razoável para um pedido B2B",
            outputKey = "proposta")
    PropostaDesconto propor(@V("descricao") String descricaoPedido);
}
