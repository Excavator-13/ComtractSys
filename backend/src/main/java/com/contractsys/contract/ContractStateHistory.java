package com.contractsys.contract;

import com.contractsys.user.SysUser;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class ContractStateHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Contract contract;

    @Enumerated(EnumType.STRING)
    private ContractStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractStatus toStatus;

    @ManyToOne(optional = false)
    private SysUser operator;

    private String remark;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setContract(Contract contract) { this.contract = contract; }
    public Contract getContract() { return contract; }
    public ContractStatus getFromStatus() { return fromStatus; }
    public void setFromStatus(ContractStatus fromStatus) { this.fromStatus = fromStatus; }
    public ContractStatus getToStatus() { return toStatus; }
    public void setToStatus(ContractStatus toStatus) { this.toStatus = toStatus; }
    public SysUser getOperator() { return operator; }
    public void setOperator(SysUser operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

