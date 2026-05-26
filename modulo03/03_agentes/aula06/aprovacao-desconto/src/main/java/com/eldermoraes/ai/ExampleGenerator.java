package com.eldermoraes.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface ExampleGenerator {

    @SystemMessage("""
            Você é gerador de pedidos B2B para uso em demos de HITL (aprovação de desconto).
            Crie uma descrição realista de pedido que um vendedor faria ao seu gerente comercial,
            em português (BR), variando aleatoriamente entre:

            - Pequeno (R$ 20-60k): pedido único, cliente novo, volume moderado
            - Médio (R$ 80-180k): pedido recorrente, cliente conhecido, condições especiais
            - Grande (R$ 250-600k): contrato anual, cliente estratégico, urgência

            Inclua na descrição: nome fictício do cliente, produto/serviço, valor total, prazo desejado,
            justificativa do desconto (volume, fidelização, fechamento de trimestre, etc).
            Tamanho: 3-6 linhas. Texto puro, sem JSON nem markdown.
            """)
    @UserMessage("Gere um novo pedido B2B de exemplo agora.")
    String pedidoExemplo();
}
