package com.contractsys.contract;

import com.contractsys.user.SysUser;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
public class ContractVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Contract contract;

    @Column(nullable = false)
    private int versionNo;

    @Column(nullable = false, length = 40)
    private String name;

    @Lob
    @Column(nullable = false)
    private String content;

    @ManyToOne(optional = false)
    private SysUser operator;

    @Column(length = 100)
    private String remark;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Contract getContract() { return contract; }
    public void setContract(Contract contract) { this.contract = contract; }
    public int getVersionNo() { return versionNo; }
    public void setVersionNo(int versionNo) { this.versionNo = versionNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public SysUser getOperator() { return operator; }
    public void setOperator(SysUser operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
