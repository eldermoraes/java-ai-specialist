package com.eldermoraes.guardrails;

import jakarta.enterprise.context.RequestScoped;
import java.util.ArrayList;
import java.util.List;

/**
 * A conta de chamadas a modelo de uma requisição. Não é guardrail: é a régua do que a
 * fila custa, lida no log sem contar à mão.
 *
 * Fica no escopo da requisição HTTP. Cada chamada a qualquer modelo (o classificador, o
 * assistente, o juiz, a triagem) entra aqui pelo ContadorDeChamadasListener, como "pequeno"
 * ou "grande", e o endpoint escreve a linha de resumo no fim: qual foi o resultado e quantas
 * chamadas custou.
 */
@RequestScoped
public class ContadorDeChamadas {

    private final List<String> modelos = new ArrayList<>();

    public void registrar(String modelo) {
        modelos.add(modelo);
    }

    public int total() {
        return modelos.size();
    }

    /** Os modelos chamados, na ordem, para a linha de resumo. */
    public List<String> modelos() {
        return List.copyOf(modelos);
    }
}
