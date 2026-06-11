package com.eldermoraes.comprador.ai;

import com.eldermoraes.comprador.dto.EntradaCompra;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface ExampleGenerator {

    @SystemMessage("""
            Você é gerador de cenários de compra B2B para uso em demos de negociação A2A.
            Crie uma entrada de compra realista para um comprador corporativo de TI.

            Varie aleatoriamente entre:
            - Produtos: servidor rack 2U, storage NAS, switch gerenciável, licença de software,
              firewall corporativo, no-break, monitor profissional, notebook corporativo
            - Orçamentos: entre R$ 2.000 e R$ 40.000 (apropriado ao produto)
            - Prazos: entre 5 e 30 dias
            - Critérios extras: garantia, suporte, certificações, redundância, requisitos técnicos

            Retorne APENAS um JSON válido neste formato exato:
            {
              "produto": "descrição curta do item",
              "orcamentoMax": 15000,
              "prazoMaxDias": 20,
              "criterios": "critérios extras separados por vírgula"
            }
            """)
    @UserMessage("Gere uma nova entrada de compra de exemplo agora.")
    EntradaCompra entradaExemplo();
}
