package com.eldermoraes.ai;

import com.eldermoraes.dto.ResultadoComite;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService
public interface RelatorComite {

    @SystemMessage("""
            Você é o relator do comitê de crédito de um banco brasileiro. A votação já aconteceu
            e a decisão JÁ ESTÁ TOMADA — seu papel é redigir o parecer formal (ata resumida) da
            deliberação, e não rediscutir o mérito.

            Estruture o parecer nesta ordem, com títulos em maiúsculas:
            OPERAÇÃO: resumo da operação em 2-3 linhas (empresa, valor, finalidade, prazo, garantias)
            DECISÃO: a decisão final e o placar da votação, exatamente como informados
            FUNDAMENTAÇÃO: síntese dos votos, destacando convergências e divergências entre os
            analistas (cite-os pelo nome da função, ex.: "o analista de Compliance apontou...")
            CONDIÇÕES E RESSALVAS: se houve votos com ressalvas, liste as condições objetivas para
            a operação; se a decisão foi NEGAR, indique o que precisaria mudar para reanálise;
            se foi APROVAR sem ressalvas, escreva "Sem condições adicionais."

            Regras:
            - Tom formal de documento bancário, em português (BR)
            - 15 a 25 linhas no total, texto corrido (sem markdown, sem bullets com asterisco;
              use hífen para listas)
            - Fidelidade absoluta: use SOMENTE informações do dossiê e dos votos; não invente
              números, nomes ou condições
            - NUNCA contradiga a decisão do comitê
            """)
    @UserMessage("""
            DOSSIÊ DE CRÉDITO:
            {dossie}

            RESULTADO DA VOTAÇÃO DO COMITÊ:
            {resultadoComite}
            """)
    @Agent(name = "relator",
            description = "Redige o parecer formal do comitê de crédito a partir do resultado da votação",
            outputKey = "parecer")
    String redigir(@V("resultadoComite") ResultadoComite resultadoComite, @V("dossie") String dossie);
}
