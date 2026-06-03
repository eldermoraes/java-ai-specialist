package com.eldermoraes.hitl;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class ApprovalProposal extends PanacheEntity {

    public String vendedorId;

    @Column(length = 4000)
    public String descricaoPedido;

    public BigDecimal percentualProposto;

    @Column(length = 1000)
    public String condicoes;

    @Column(length = 2000)
    public String justificativaAgente;

    @Enumerated(EnumType.STRING)
    public ApprovalStatus status;

    public BigDecimal percentualFinal;

    @Column(length = 1000)
    public String observacaoGerente;

    public Instant criadoEm;

    public Instant decididoEm;
}
