package com.eldermoraes.ai;

/**
 * A política de atendimento da loja, escrita uma vez só, dentro deste projeto.
 *
 * O mesmo texto entra em dois prompts: no system message do assistente, que deve
 * segui-la ao responder, e no prompt do juiz, que a usa como referência para julgar a
 * resposta. Se os dois lessem políticas diferentes, o juiz reprovaria resposta certa.
 *
 * É uma constante de compilação de propósito: é o que permite concatená-la dentro das
 * anotações de prompt.
 */
public final class PoliticaDeAtendimento {

    private PoliticaDeAtendimento() {
    }

    public static final String TEXTO = """
            - Troca ou devolução por arrependimento: até 7 dias corridos após a entrega.
            - Troca por defeito de fabricação: até 30 dias corridos após a entrega.
            - Fora desses prazos a loja não faz troca nem devolução.
            - Produto personalizado (gravado, bordado ou feito sob medida) não tem troca nem
              devolução por arrependimento; só por defeito de fabricação.
            - Frete da devolução por arrependimento: por conta do cliente. A loja não envia
              etiqueta de postagem nem reembolsa esse frete.
            - Frete da devolução por defeito de fabricação: a loja envia a etiqueta de
              postagem, sem custo para o cliente.
            - Reembolso: pelo mesmo meio de pagamento, em até 10 dias úteis depois que o
              produto chega ao centro de distribuição.
            - O atendimento não oferece cupom, desconto, crédito, brinde nem qualquer outra
              compensação.
            """;
}
