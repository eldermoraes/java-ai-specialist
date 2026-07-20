package com.eldermoraes.workflow;

import com.eldermoraes.ai.AgenteEcossistema;
import com.eldermoraes.ai.AgenteRepo;
import com.eldermoraes.dto.DecisaoDesk;
import dev.langchain4j.agentic.declarative.Output;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.service.V;

/**
 * A cola do balcão: quatro passos em sequência.
 *
 * <p>São os mesmos {@code @SequenceAgent}/{@code outputKey}/{@code subAgents} do módulo de
 * Agentes: a costura não muda. A novidade da aula inteira está no {@code @McpToolBox} de
 * cada sub-agente: analisar o repositório → confrontar com o ecossistema → gate humano →
 * registrar (se aprovado). O padrão é o de sempre; o que mudou foi o alcance de cada membro.
 */
public interface DecisionDeskAgent {

    @SequenceAgent(
            outputKey = "decisao",
            subAgents = {
                    AgenteRepo.class,         // lê o repositório local (caixa: filesystem)
                    AgenteEcossistema.class,  // confronta com a doc externa (caixa: deepwiki)
                    GateAprovacao.class,      // @HumanInTheLoop: grava ou não?
                    RegistroDecisao.class     // grava o ADR (caixa: filesystem), só se aprovado
            })
    DecisaoDesk decidir(@V("pergunta") String pergunta);

    @Output
    static DecisaoDesk assemble(AgenticScope scope) {
        String pergunta = (String) scope.readState("pergunta");
        String analiseRepo = (String) scope.readState("analiseRepo");
        String visaoEcossistema = (String) scope.readState("visaoEcossistema");
        String aprovacao = (String) scope.readState("aprovacao");
        String registro = (String) scope.readState("registro");
        if (registro == null) {
            registro = "Decisão não registrada — aprovação negada, nenhum write_file executado.";
        }
        return new DecisaoDesk(pergunta, analiseRepo, visaoEcossistema, aprovacao, registro);
    }
}
