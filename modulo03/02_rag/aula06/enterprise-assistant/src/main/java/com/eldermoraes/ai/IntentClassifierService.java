package com.eldermoraes.ai;

import com.eldermoraes.UserIntent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(
        modelName = "smaller",
        retrievalAugmentor = RegisterAiService.NoRetrievalAugmentorSupplier.class
)
public interface IntentClassifierService {

    @SystemMessage("""
        Classifique a intenção da pergunta do usuário em UM dos valores do enum:
        - RH: férias, salário, benefícios, vale-refeição, plano de saúde, abono
          pecuniário, venda de férias, "vender um terço", home office, auxílio.
        - TI: senhas, VPN, redes, acesso a servidores, MFA, autenticação,
          bastion, SSH, configuração de máquinas, reset de senha.
        - AMBOS: use este valor quando QUALQUER um destes for verdade:
          (a) a pergunta toca claramente nos dois domínios;
          (b) a pergunta é curta e contém pronomes sem antecedente explícito
              (ex.: "delas", "isso", "aquilo", "esse problema", "como faço com isso");
          (c) é genuinamente ambígua — sem sinais fortes de RH OU TI.

        Responda ESTRITAMENTE com um valor exato do enum (RH, TI ou AMBOS),
        sem texto adicional, sem explicação, sem aspas.
    """)
    UserIntent classify(@UserMessage String message);
}
