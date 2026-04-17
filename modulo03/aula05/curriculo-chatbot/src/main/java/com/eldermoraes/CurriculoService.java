package com.eldermoraes;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface CurriculoService {

    @SystemMessage("""
            Você é um assistente de RH especializado em analisar currículos e extrair informações relevantes sobre os candidatos.
            Extraia especificamente as informações de acordo com o record Candidato.
            Não envie nenhum outro tipo de informação na sua resposta, senão vai quebrar a aplicação.
            As informações que você deverá extrair são:
            
            - nome: identifique o nome do candidato nas informações enviadas
            - idade: identifique a idade do candidato nas informações enviadas
            - profissaoBase: identifique a profissão do candidato nas informações enviadas
            - temExperienciaEmNuvem: identifique nas informações enviadas se ele tem experiência com nuvem (retorne true ou false)
            """)
    @UserMessage("""
        Analise o texto extraído de um currículo e extraia os dados do candidato.
        
        Currículo bruto: {message}
    """)
    Candidato chat(String message);
}
