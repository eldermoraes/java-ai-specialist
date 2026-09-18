package com.eldermoraes.ai;

import com.eldermoraes.guardrails.DadoSensivelGuardrail;
import com.eldermoraes.guardrails.FormatoGuardrail;
import com.eldermoraes.guardrails.RegraDeNegocioGuardrail;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * Triagem de chamados de suporte interno.
 *
 * O que interessa nesta aula é a anotação dos dois métodos. Ela declara os guardrails que
 * rodam DEPOIS da resposta do modelo e antes de ela ser devolvida. A ordem da lista é a
 * ordem de execução: formato, regra de negócio, dado sensível.
 *
 * Os dois métodos declaram a mesma fila e mudam só a system message. A diferença entre eles
 * é o que se aprende aqui: pedir o formato no prompt reduz o erro de formato, e não elimina.
 */
@RegisterAiService
public interface TriagemDeChamados {

    @SystemMessage("""
            Você faz a triagem dos chamados de suporte interno da empresa.

            Para cada chamado, defina a categoria do problema, a prioridade de atendimento e
            um resumo de uma frase.

            Responda apenas com um JSON com os campos categoria, prioridade e resumo, sem
            texto fora do JSON. Em prioridade use exatamente um destes valores: baixa, media
            ou alta.
            """)
    @OutputGuardrails({
            FormatoGuardrail.class,
            RegraDeNegocioGuardrail.class,
            DadoSensivelGuardrail.class
    })
    String triar(String chamado);

    @SystemMessage("""
            Você faz a triagem dos chamados de suporte interno da empresa.

            Para cada chamado, defina a categoria do problema, a prioridade de atendimento
            entre baixa, media e alta, e um resumo de uma frase.
            """)
    @OutputGuardrails({
            FormatoGuardrail.class,
            RegraDeNegocioGuardrail.class,
            DadoSensivelGuardrail.class
    })
    String triarSemFormato(String chamado);
}
