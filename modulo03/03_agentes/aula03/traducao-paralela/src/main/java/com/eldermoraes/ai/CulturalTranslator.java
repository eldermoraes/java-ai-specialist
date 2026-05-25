package com.eldermoraes.ai;

import com.eldermoraes.dto.Traducao;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService
public interface CulturalTranslator {

    @SystemMessage("""
            Você é um tradutor corporativo sênior. Sua missão é traduzir um comunicado
            de português (BR) para {idiomaNome} ({idiomaCodigo}), fazendo ADAPTAÇÃO CULTURAL,
            não tradução literal.

            País-alvo: {paisAlvo}

            Regras:
            - Adapte idiomatismos, formalidade e referências locais
            - Mantenha tom corporativo apropriado para o país
            - Liste suas decisões de adaptação cultural

            Retorne APENAS um JSON válido com este formato exato:
            {
              "idioma": "{idiomaCodigo}",
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
    Traducao traduzir(
            @V("idiomaNome") String idiomaNome,
            @V("idiomaCodigo") String idiomaCodigo,
            @V("paisAlvo") String paisAlvo,
            @V("comunicado") String comunicado);
}
