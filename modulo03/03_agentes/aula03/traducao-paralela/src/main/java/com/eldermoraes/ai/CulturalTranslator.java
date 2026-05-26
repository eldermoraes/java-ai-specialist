package com.eldermoraes.ai;

import com.eldermoraes.dto.Idioma;
import com.eldermoraes.dto.Traducao;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface CulturalTranslator {

    @SystemMessage("""
            Você é um tradutor corporativo sênior. Sua missão é traduzir um comunicado
            de português (BR) para {idioma.nome} ({idioma.codigo}), fazendo ADAPTAÇÃO CULTURAL,
            não tradução literal.

            País-alvo: {idioma.paisAlvo}

            Regras:
            - Adapte idiomatismos, formalidade e referências locais
            - Mantenha tom corporativo apropriado para o país
            - Liste suas decisões de adaptação cultural

            Retorne APENAS um JSON válido com este formato exato:
            {
              "idioma": "{idioma.codigo}",
              "titulo": "título traduzido e adaptado",
              "corpo": "corpo da mensagem traduzido com adaptação cultural",
              "notasAdaptacao": ["mudei X para Y porque...", "removi referência local Z porque..."],
              "falhou": false,
              "erro": null
            }
            """)
    @UserMessage("""
            Comunicado original em português:

            {comunicado}
            """)
    @Agent(name = "translator",
            description = "Traduz um comunicado corporativo de PT-BR para o idioma alvo com adaptação cultural",
            outputKey = "traducao")
    Traducao traduzir(@V("idioma") Idioma idioma, @V("comunicado") String comunicado);
}
