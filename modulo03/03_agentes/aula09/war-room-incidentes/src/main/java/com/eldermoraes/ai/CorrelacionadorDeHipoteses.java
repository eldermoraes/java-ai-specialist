package com.eldermoraes.ai;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService
public interface CorrelacionadorDeHipoteses {

    @SystemMessage("""
            Você é o coordenador técnico de uma war-room de incidente de produção.
            Três especialistas já publicaram evidências no quadro compartilhado: aplicação,
            infraestrutura e banco de dados. Sua responsabilidade é CRUZAR as três evidências
            e formular a hipótese de causa mais provável do incidente.

            Raciocine sobre a cadeia de causalidade: o que degradou PRIMEIRO e o que é apenas
            consequência? Sintomas na aplicação frequentemente são efeito de problemas em
            infraestrutura ou banco — e vice-versa.

            Responda em português (BR), texto puro (sem JSON nem markdown), neste formato exato:

            HIPÓTESE PRINCIPAL: descrição objetiva da causa mais provável e da cadeia de causalidade.
            HIPÓTESE ALTERNATIVA: segunda explicação plausível, e o que a diferenciaria da principal.
            EVIDÊNCIAS DE SUPORTE: bullets "- " citando QUAL especialista publicou cada evidência usada.
            DESCARTADO: bullets "- " do que foi considerado e descartado, com o motivo.

            Não invente evidências que não estejam no quadro. Nunca retorne uma resposta vazia.
            """)
    @UserMessage("""
            QUADRO DA WAR-ROOM — EVIDÊNCIAS PUBLICADAS:

            [APLICAÇÃO]
            {evidenciaAplicacao}

            [INFRAESTRUTURA]
            {evidenciaInfra}

            [BANCO DE DADOS]
            {evidenciaBanco}
            """)
    @Agent(name = "correlacionador",
            description = "Cruza as evidências de aplicação, infraestrutura e banco e formula a hipótese de causa mais provável",
            outputKey = "hipotese")
    String correlacionar(@V("evidenciaAplicacao") String evidenciaAplicacao,
                         @V("evidenciaInfra") String evidenciaInfra,
                         @V("evidenciaBanco") String evidenciaBanco);
}
