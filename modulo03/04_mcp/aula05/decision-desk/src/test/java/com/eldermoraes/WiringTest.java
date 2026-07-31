package com.eldermoraes;

import com.eldermoraes.ai.AgenteEcossistema;
import com.eldermoraes.ai.AgenteRepo;
import com.eldermoraes.ai.ExampleGenerator;
import com.eldermoraes.hitl.ApprovalService;
import com.eldermoraes.workflow.DecisionDeskAgent;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Prova de montagem, não de modelo. Subir o container do Quarkus já exige que os AI services,
 * o workflow agêntico ({@code @SequenceAgent}) e os clients MCP declarados estejam bem
 * construídos e injetáveis. Nenhum modelo é chamado aqui: se as peças não estiverem bem
 * ligadas, a aplicação nem sobe.
 */
@QuarkusTest
class WiringTest {

    @Inject
    DecisionDeskAgent decisionDeskAgent;

    @Inject
    AgenteRepo agenteRepo;

    @Inject
    AgenteEcossistema agenteEcossistema;

    @Inject
    ExampleGenerator exampleGenerator;

    @Inject
    ApprovalService approvalService;

    @Test
    void contextoSobeComToDaAFiacao() {
        assertNotNull(decisionDeskAgent);
        assertNotNull(agenteRepo);
        assertNotNull(agenteEcossistema);
        assertNotNull(exampleGenerator);
        assertNotNull(approvalService);
    }
}
