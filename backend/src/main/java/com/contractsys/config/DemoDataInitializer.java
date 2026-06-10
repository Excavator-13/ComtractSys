package com.contractsys.config;

import com.contractsys.contract.*;
import com.contractsys.customer.Customer;
import com.contractsys.customer.CustomerRepository;
import com.contractsys.user.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
@Order(20)
public class DemoDataInitializer implements CommandLineRunner {
    private static final String DEMO_PREFIX = "demo_";
    private final boolean enabled;
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CustomerRepository customerRepository;
    private final ContractRepository contractRepository;
    private final ContractVersionRepository versionRepository;
    private final ContractStateHistoryRepository historyRepository;
    private final ContractTaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(@Value("${app.demo-data.enabled:false}") boolean enabled,
                               JdbcTemplate jdbcTemplate,
                               UserRepository userRepository,
                               RoleRepository roleRepository,
                               CustomerRepository customerRepository,
                               ContractRepository contractRepository,
                               ContractVersionRepository versionRepository,
                               ContractStateHistoryRepository historyRepository,
                               ContractTaskRepository taskRepository,
                               PasswordEncoder passwordEncoder) {
        this.enabled = enabled;
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.customerRepository = customerRepository;
        this.contractRepository = contractRepository;
        this.versionRepository = versionRepository;
        this.historyRepository = historyRepository;
        this.taskRepository = taskRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            cleanupDemoData();
            return;
        }
        seedDemoData();
    }

    private void seedDemoData() {
        SysUser drafter = user("demo_drafter", "演示起草员", "ROLE_OPERATOR");
        SysUser manager = user("demo_manager", "演示合同管理员", "ROLE_CONTRACT_ADMIN");
        SysUser approver = user("demo_approver", "演示审批员", "ROLE_OPERATOR");

        Customer customer = customerRepository.findByDeletedFalseAndNameContainingIgnoreCase(
                        "演示客户A", org.springframework.data.domain.PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseGet(() -> {
                    Customer c = new Customer();
                    c.setCustomerNo("DEMO-CUSTOMER-0001");
                    c.setName("演示客户A");
                    c.setAddress("上海市演示路 100 号");
                    c.setTel("13800000001");
                    c.setRemark("演示数据，可由启动清理逻辑删除");
                    return customerRepository.save(c);
                });

        if (contractRepository.search("演示采购合同", null, org.springframework.data.domain.PageRequest.of(0, 1)).hasContent()) {
            return;
        }

        Contract contract = new Contract();
        contract.setContractNo("DEMO-CONTRACT-0001");
        contract.setName("演示采购合同");
        contract.setCustomer(customer);
        contract.setBeginDate(LocalDate.now());
        contract.setEndDate(LocalDate.now().plusMonths(6));
        contract.setContent("演示采购合同正文\n\n一、采购标的\n二、交付验收\n三、付款方式");
        contract.setDrafter(drafter);
        contract.setStatus(ContractStatus.ASSIGNED);
        contractRepository.save(contract);

        ContractVersion version = new ContractVersion();
        version.setContract(contract);
        version.setVersionNo(1);
        version.setName(contract.getName());
        version.setContent(contract.getContent());
        version.setOperator(drafter);
        version.setRemark("演示起草");
        versionRepository.save(version);

        ContractStateHistory history = new ContractStateHistory();
        history.setContract(contract);
        history.setFromStatus(null);
        history.setToStatus(ContractStatus.DRAFT);
        history.setOperator(drafter);
        history.setRemark("演示起草合同");
        historyRepository.save(history);

        ContractTask task = new ContractTask();
        task.setContract(contract);
        task.setAssignee(approver);
        task.setTaskType(TaskType.COUNTERSIGN);
        task.setTaskStatus(TaskStatus.PENDING);
        taskRepository.save(task);

        ContractTask assignTask = new ContractTask();
        assignTask.setContract(contract);
        assignTask.setAssignee(manager);
        assignTask.setTaskType(TaskType.ASSIGN);
        assignTask.setTaskStatus(TaskStatus.DONE);
        assignTask.setOpinion("演示分配完成");
        assignTask.setOperatedAt(java.time.LocalDateTime.now());
        taskRepository.save(assignTask);
    }

    private SysUser user(String username, String displayName, String roleCode) {
        return userRepository.findByUsernameAndDeletedFalse(username).orElseGet(() -> {
            SysRole role = roleRepository.findByRoleCode(roleCode)
                    .orElseThrow(() -> new IllegalStateException("Missing role " + roleCode));
            SysUser user = new SysUser();
            user.setUsername(username);
            user.setDisplayName(displayName);
            user.setPasswordHash(passwordEncoder.encode("123456"));
            user.getRoles().add(role);
            return userRepository.save(user);
        });
    }

    private void cleanupDemoData() {
        List<Long> contractIds = jdbcTemplate.queryForList(
                "select id from contract where name like '演示%'", Long.class);
        List<Long> userIds = jdbcTemplate.queryForList(
                "select id from sys_user where username like ?", Long.class, DEMO_PREFIX + "%");
        if (!contractIds.isEmpty()) {
            deleteByContractIds("delete from attachment where contract_id in (%s)", contractIds);
            deleteByContractIds("delete from contract_version where contract_id in (%s)", contractIds);
            deleteByContractIds("delete from contract_task where contract_id in (%s)", contractIds);
            deleteByContractIds("delete from contract_state_history where contract_id in (%s)", contractIds);
            deleteByContractIds("delete from contract where id in (%s)", contractIds);
        }
        jdbcTemplate.update("delete from customer where name like '演示%'");
        jdbcTemplate.update("delete from operation_log where content like '%演示%'");
        if (!userIds.isEmpty()) {
            deleteByIds("delete from operation_log where operator_id in (%s)", userIds);
            deleteByIds("delete from sys_user_role where user_id in (%s)", userIds);
            deleteByIds("delete from sys_user where id in (%s)", userIds);
        }
    }

    private void deleteByContractIds(String sql, List<Long> ids) {
        deleteByIds(sql, ids);
    }

    private void deleteByIds(String sql, List<Long> ids) {
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        jdbcTemplate.update(String.format(sql, placeholders), ids.toArray());
    }
}
