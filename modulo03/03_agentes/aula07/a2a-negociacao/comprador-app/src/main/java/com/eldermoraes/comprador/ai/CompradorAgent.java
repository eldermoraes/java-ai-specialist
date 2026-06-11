package com.eldermoraes.comprador.ai;

import com.eldermoraes.comprador.dto.DecisaoComprador;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;

@ApplicationScoped
@RegisterAiService
public interface CompradorAgent {

    @SystemMessage("""
            Você é comprador(a) B2B negociando "{produto}".

            CONSTRAINTS HARDS:
            - Orçamento MÁXIMO: R$ {orcamentoMax} (não pode ultrapassar JAMAIS)
            - Prazo máximo aceitável: {prazoMaxDias} dias
            - Critérios extras: {criterios}

            Estratégia:
            - Rodada 1-2: comece tentando 20% abaixo da proposta do vendedor (anchoring)
            - Rodadas seguintes: ceda gradativamente até no máximo o orçamento
            - Se o vendedor oferecer preço dentro do orçamento E condições aceitáveis: ACEITAR
            - Se vendedor sinalizou "limiteAtingido" e o preço ainda excede o orçamento: DESISTIR
            - Caso contrário: CONTRAPOR com novo preço alvo

            Retorne APENAS um JSON válido:
            {
              "acao": "ACEITAR|CONTRAPOR|DESISTIR",
              "precoSugerido": 13500,
              "mensagem": "mensagem para o vendedor em texto natural (1-2 frases)",
              "justificativa": "raciocínio interno (não enviado ao vendedor)"
            }

            precoSugerido só faz sentido em CONTRAPOR (use 0 ou null nas outras ações).
            """)
    @UserMessage("""
            Rodada {rodada}/{maxRodadas}.
            Proposta atual do vendedor: R$ {precoVendedor}, prazo {prazoVendedor}, condições: {condicoes}
            Mensagem do vendedor: "{mensagemVendedor}"
            Vendedor sinalizou limite atingido: {limiteAtingido}
            """)
    @Agent(name = "comprador",
            description = "Agente comprador B2B — avalia proposta do vendedor e decide ACEITAR, CONTRAPOR ou DESISTIR respeitando orçamento e critérios",
            outputKey = "decisaoComprador")
    DecisaoComprador avaliar(
            @V("produto") String produto,
            @V("orcamentoMax") BigDecimal orcamentoMax,
            @V("prazoMaxDias") int prazoMaxDias,
            @V("criterios") String criterios,
            @V("rodada") int rodada,
            @V("maxRodadas") int maxRodadas,
            @V("precoVendedor") BigDecimal precoVendedor,
            @V("prazoVendedor") String prazoVendedor,
            @V("condicoes") String condicoes,
            @V("mensagemVendedor") String mensagemVendedor,
            @V("limiteAtingido") boolean limiteAtingido);
}
