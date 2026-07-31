package com.eldermoraes.workflow;

import com.eldermoraes.hitl.ApprovalService;
import dev.langchain4j.agentic.declarative.HumanInTheLoop;
import dev.langchain4j.service.V;
import jakarta.enterprise.inject.spi.CDI;

/**
 * O gate humano binário: grava ou não grava?
 *
 * <p>Operações destrutivas nunca são autônomas. O {@code write_file} do {@link
 * com.eldermoraes.ai.EscritorAdr} usa um server MCP de terceiros: código que você não
 * escreveu, do outro lado do protocolo. Ele só dispara depois de um "sim" humano.
 *
 * <p>O método é estático (exigência do {@code @HumanInTheLoop}) e bloqueia a sequência até
 * a resposta chegar: por baixo, um {@code CompletableFuture}, o mesmo mecanismo da aprovação
 * de desconto do módulo de Agentes. Lá a decisão era ternária (aprovar/rejeitar/contrapor) e
 * exigiu Java puro; aqui ela é binária (sim/não), que é exatamente o caso para o qual a
 * anotação declarativa foi feita.
 *
 * <p>Como uma interface não aceita {@code @Inject}, alcançamos o {@link ApprovalService}
 * pelo CDI programático.
 */
public interface GateAprovacao {

    @HumanInTheLoop(
            description = "Pergunta ao humano se o mini-ADR deve ser gravado no disco (sim/não)",
            outputKey = "aprovacao")
    static String pedirAprovacao(@V("visaoEcossistema") String visaoEcossistema) {
        return CDI.current().select(ApprovalService.class).get().aguardarDecisao(visaoEcossistema);
    }
}
