package com.eldermoraes.vendedor.ai;

import com.eldermoraes.vendedor.dto.RespostaVendedor;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

import java.math.BigDecimal;

@ApplicationScoped
@RegisterAiService
public interface VendedorAgent {

    @SystemMessage("""
            Você é vendedor(a) B2B sênior negociando o produto "{produto}".

            CONSTRAINTS HARDS — você NUNCA pode violar:
            - Preço de tabela: R$ {precoTabela}
            - Preço MÍNIMO (chão duro): R$ {precoMinimo} — não vá abaixo disso JAMAIS
            - Prazo padrão de entrega: {prazoPadrao}

            Estratégia:
            - Rodada 1: ofereça próximo do preço de tabela (5-10% de desconto se justificado)
            - Rodadas seguintes: ceda gradativamente até no máximo o preço mínimo
            - Se o comprador pedir abaixo do mínimo, recuse educadamente e informe que não consegue ir abaixo
            - Sempre proponha condições adicionais para compensar (prazo de pagamento, frete, garantia)

            Retorne APENAS um JSON válido:
            {
              "produto": "{produto}",
              "precoOferecido": 14800,
              "prazoEntrega": "12 dias úteis",
              "condicoes": "pagamento em 30/60/90 dias, frete por nossa conta",
              "mensagem": "resposta em texto natural (1-3 frases)",
              "limiteAtingido": false
            }

            limiteAtingido = true APENAS quando você já chegou no preço mínimo.
            """)
    @UserMessage("""
            Rodada atual: {rodada}
            Último valor proposto pelo comprador: R$ {ultimoValor}
            Mensagem do comprador: "{mensagem}"
            """)
    @Agent(name = "vendedor",
            description = "Agente vendedor B2B — propõe preço e condições respeitando o preço mínimo do catálogo",
            outputKey = "respostaVendedor")
    RespostaVendedor responder(
            @V("produto") String produto,
            @V("precoTabela") BigDecimal precoTabela,
            @V("precoMinimo") BigDecimal precoMinimo,
            @V("prazoPadrao") String prazoPadrao,
            @V("rodada") int rodada,
            @V("ultimoValor") BigDecimal ultimoValor,
            @V("mensagem") String mensagem);
}
