package com.chris64233.freightclaim.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 结算单分录行：一次结算落到具体责任分录（承运段）上的金额。 */
@Entity
@Table(name = "settlement_line")
public class SettlementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private Settlement settlement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "liability_entry_id", nullable = false)
    private LiabilityEntry liabilityEntry;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    protected SettlementLine() {
    }

    public SettlementLine(Settlement settlement, LiabilityEntry liabilityEntry, BigDecimal amount) {
        this.settlement = settlement;
        this.liabilityEntry = liabilityEntry;
        this.amount = Money.of(amount);
    }

    public Long getId() {
        return id;
    }

    public Settlement getSettlement() {
        return settlement;
    }

    public LiabilityEntry getLiabilityEntry() {
        return liabilityEntry;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
