package com.chris64233.freightclaim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** 索赔证据：随索赔提交或后续补充的损失证明材料。 */
@Entity
@Table(name = "claim_evidence", uniqueConstraints = {
        @UniqueConstraint(name = "uk_claim_evidence_ref", columnNames = {"claim_id", "evidence_ref"})
})
public class ClaimEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private Claim claim;

    /** 凭证编号/影像号。 */
    @Column(name = "evidence_ref", nullable = false, length = 128)
    private String evidenceRef;

    /** 证据类型（照片、签收单、检验报告等）。 */
    @Column(name = "evidence_type", length = 32)
    private String evidenceType;

    @Column(length = 512)
    private String summary;

    protected ClaimEvidence() {
    }

    public ClaimEvidence(Claim claim, String evidenceRef, String evidenceType, String summary) {
        this.claim = claim;
        this.evidenceRef = evidenceRef;
        this.evidenceType = evidenceType;
        this.summary = summary;
    }

    public Long getId() {
        return id;
    }

    public Claim getClaim() {
        return claim;
    }

    public String getEvidenceRef() {
        return evidenceRef;
    }

    public String getEvidenceType() {
        return evidenceType;
    }

    public String getSummary() {
        return summary;
    }
}
