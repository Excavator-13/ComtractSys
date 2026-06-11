package com.contractsys.contract;

import com.contractsys.common.ApiException;
import com.contractsys.user.SysUser;
import org.springframework.stereotype.Component;

@Component
public class ContractAccessGuard {
    private final ContractRepository contractRepository;
    private final ContractTaskRepository taskRepository;

    public ContractAccessGuard(ContractRepository contractRepository, ContractTaskRepository taskRepository) {
        this.contractRepository = contractRepository;
        this.taskRepository = taskRepository;
    }

    public Contract getContract(Long id) {
        return contractRepository.findById(id).filter(contract -> !contract.isDeleted())
                .orElseThrow(() -> ApiException.notFound("合同不存在"));
    }

    public Contract getContractForUpdate(Long id) {
        return contractRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("合同不存在"));
    }

    public void ensureCanViewContract(Long contractId, SysUser user) {
        ensureCanViewContract(getContract(contractId), user);
    }

    public void ensureCanViewContract(Contract contract, SysUser user) {
        if (!canViewContract(contract, user)) {
            throw ApiException.forbidden("当前用户无权查看该合同");
        }
    }

    public void ensureCanModifyContract(Long contractId, SysUser user) {
        Contract contract = getContract(contractId);
        ensureCanModifyContract(contract, user);
    }

    public void ensureCanModifyContract(Contract contract, SysUser user) {
        ensureCanViewContract(contract, user);
        ensureMutableContract(contract);
    }

    public boolean canViewContract(Contract contract, SysUser user) {
        return hasPermission(user, "contract:query")
                || contract.getDrafter().getId().equals(user.getId())
                || taskRepository.existsByContractIdAndAssigneeId(contract.getId(), user.getId())
                || hasPermission(user, "contract:assign");
    }

    public boolean hasPermission(SysUser user, String permissionCode) {
        return user.hasPermission(permissionCode);
    }

    public void ensureMutableContract(Contract contract) {
        if (contract.getStatus() == ContractStatus.CANCELLED) {
            throw ApiException.conflict("已取消合同不能继续操作");
        }
    }
}
