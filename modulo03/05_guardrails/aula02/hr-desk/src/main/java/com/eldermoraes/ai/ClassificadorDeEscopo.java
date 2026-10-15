package com.eldermoraes.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

/**
 * O classificador que o guardrail de escopo por sentido consulta.
 *
 * Três escolhas explicam por que um modelo cabe aqui dentro de um guardrail:
 *
 * - a tarefa é estreita: ele não responde nada, só decide se a pergunta é de RH;
 * - o veredito é um enum, não texto livre: o código consegue ler a resposta sem
 *   interpretar frase;
 * - roda no modelo "smaller", configurado em application.properties, e não no modelo
 *   grande que responde ao usuário.
 *
 * Temperatura zero: a mesma pergunta deve receber o mesmo veredito. "Deve" é o mais
 * longe que dá para ir. Este é o componente não determinístico do projeto.
 */
@RegisterAiService(modelName = "smaller")
public interface ClassificadorDeEscopo {

    enum Veredito {
        DENTRO,
        FORA
    }

    @SystemMessage("""
            Você classifica perguntas dirigidas ao assistente de RH de uma empresa.

            Responda DENTRO se a pergunta for sobre recursos humanos: férias, benefícios,
            folha de pagamento, ponto, admissão, desligamento, política interna, salário,
            atestado, home office.

            Responda FORA para qualquer outro assunto, mesmo que a pergunta seja educada,
            útil ou pareça inofensiva.

            Responda apenas DENTRO ou FORA, sem explicação.
            """)
    @UserMessage("Pergunta: {pergunta}")
    Veredito classificar(String pergunta);
}
