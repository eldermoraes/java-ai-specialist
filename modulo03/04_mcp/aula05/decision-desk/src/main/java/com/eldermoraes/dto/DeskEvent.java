package com.eldermoraes.dto;

/**
 * Evento que o backend empurra para a UI pelo WebSocket, marcando cada fase do fluxo.
 *
 * <p>A fase {@code AGUARDANDO_APROVACAO} carrega, em {@code detalhe}, a proposta de
 * mini-ADR que o humano precisa aprovar ou negar: é o momento do gate.
 */
public record DeskEvent(String fase, String detalhe, DecisaoDesk decisao) {

    public static final String RECEBIDO = "RECEBIDO";
    public static final String AGUARDANDO_APROVACAO = "AGUARDANDO_APROVACAO";
    public static final String APROVACAO_RECEBIDA = "APROVACAO_RECEBIDA";
    public static final String DECIDIDA = "DECIDIDA";
    public static final String ERRO = "ERRO";

    public static DeskEvent recebido() {
        return new DeskEvent(RECEBIDO, null, null);
    }

    public static DeskEvent aguardandoAprovacao(String proposta) {
        return new DeskEvent(AGUARDANDO_APROVACAO, proposta, null);
    }

    public static DeskEvent aprovacaoRecebida(String resposta) {
        return new DeskEvent(APROVACAO_RECEBIDA, resposta, null);
    }

    public static DeskEvent decidida(DecisaoDesk decisao) {
        return new DeskEvent(DECIDIDA, null, decisao);
    }

    public static DeskEvent erro(String mensagem) {
        return new DeskEvent(ERRO, mensagem, null);
    }
}
