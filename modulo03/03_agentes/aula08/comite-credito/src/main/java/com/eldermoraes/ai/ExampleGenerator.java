package com.eldermoraes.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
@RegisterAiService(modelName = "smaller")
public interface ExampleGenerator {

    @SystemMessage("""
            Você é gerador de dossiês de crédito PJ para uso em demos de comitê de crédito.
            Crie um dossiê realista de uma empresa brasileira fictícia solicitando crédito, incluindo:
            - Empresa: nome fictício, setor de atuação e cidade/UF
            - Tempo de atividade e número aproximado de funcionários
            - Faturamento anual e endividamento atual
            - Operação: valor solicitado, finalidade (capital de giro, expansão, equipamentos…) e prazo
            - Garantias oferecidas (ou a ausência delas)
            - Situação cadastral: protestos, negativações ou "sem restrições"
            - Relacionamento com o banco: tempo de conta, produtos contratados, pontualidade

            Varie aleatoriamente o perfil de risco entre:
            - empresa saudável (números sólidos, garantia real, cadastro limpo)
            - empresa arriscada (endividada, sem garantias, com restrições)
            - caso intermediário/dividido (pontos fortes E fracos misturados, ex.: cresce rápido,
              mas pede valor alto sem garantia; ou tem garantia ótima, mas restrição antiga)
            Os casos intermediários devem ser os mais frequentes, para dividir o comitê.

            Varie também setor (indústria, varejo, agro, serviços, tecnologia, construção) e porte.
            Tamanho: 10-18 linhas. Texto puro: não use JSON nem markdown (proibido usar asteriscos,
            negrito ou títulos com #) — apenas linhas no formato "Rótulo: valor".
            """)
    @UserMessage("Gere um novo dossiê de crédito de exemplo agora.")
    String dossieExemplo();
}
