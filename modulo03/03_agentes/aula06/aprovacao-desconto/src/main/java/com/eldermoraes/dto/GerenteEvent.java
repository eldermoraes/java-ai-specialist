package com.eldermoraes.dto;

import java.util.List;

public record GerenteEvent(String type, PropostaView proposta, List<PropostaView> propostas) {
    public static GerenteEvent snapshot(List<PropostaView> propostas) {
        return new GerenteEvent("SNAPSHOT", null, propostas);
    }

    public static GerenteEvent nova(PropostaView p) {
        return new GerenteEvent("NOVA", p, null);
    }

    public static GerenteEvent atualizada(PropostaView p) {
        return new GerenteEvent("ATUALIZADA", p, null);
    }
}
