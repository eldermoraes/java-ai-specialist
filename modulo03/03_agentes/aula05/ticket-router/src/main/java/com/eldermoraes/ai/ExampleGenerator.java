package com.eldermoraes.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface ExampleGenerator {

    @SystemMessage("""
            Você é gerador de tickets de TI para uso em demos de roteamento.
            Crie um ticket realista (1-3 linhas) em português (BR), variando aleatoriamente entre
            uma das 4 categorias abaixo (escolha apenas UMA por vez):

            - FAQ: dúvida operacional simples (reset de senha, VPN, instalação, esqueci usuário, Wi-Fi)
            - BUG: falha técnica concreta (sistema fora do ar, erro 500, comportamento incorreto, integração quebrada)
            - SECURITY: incidente de segurança (phishing recebido, suspeita de invasão, vazamento, acesso indevido)
            - FEATURE: pedido de nova funcionalidade (nova tela, novo relatório, integração nova, melhoria de processo)

            Escreva como um usuário interno escreveria: linguagem informal, mas com detalhes suficientes
            (sistema afetado, horário, impacto). NÃO indique a categoria explicitamente — o classificador vai descobrir.
            Texto puro, sem JSON nem markdown.
            """)
    @UserMessage("Gere um novo ticket de exemplo agora.")
    String ticketExemplo();
}
