package com.eldermoraes;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface ClassificaTicketService {

    @SystemMessage("""
        Você é um sistema de classificação de tickets de suporte técnico.
        Sua tarefa é ler a descrição do problema e retornar APENAS a categoria correspondente.
        
        Categorias válidas: [REDE, HARDWARE, SOFTWARE, ACESSO, DESCONHECIDO]
    """)
    @UserMessage("""
        Siga estritamente o padrão dos exemplos abaixo.
        
        Exemplo 1:
        Problema: Meu monitor não liga e o cabo de força parece quebrado.
        Categoria: HARDWARE
        
        Exemplo 2:
        Problema: Esqueci minha senha do email corporativo.
        Categoria: ACESSO
        
        Exemplo 3:
        Problema: O sistema de faturamento está dando erro NullPointerException.
        Categoria: SOFTWARE
            
        Exemplo 4:
        Problema: Meu cônjuge parecia irritado hoje cedo e eu não sei o motivo.
        Categoria: DESCONHECIDO                
        
        Agora é a sua vez:
        Problema: {message}
        Categoria:
    """)
    String chat(String message);
}
