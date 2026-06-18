package com.contractsys.config;

import com.contractsys.contract.Contract;
import com.contractsys.contract.ContractRepository;
import com.contractsys.contract.ContractStateHistory;
import com.contractsys.contract.ContractStateHistoryRepository;
import com.contractsys.contract.ContractStatus;
import com.contractsys.contract.ContractTask;
import com.contractsys.contract.ContractTaskRepository;
import com.contractsys.contract.ContractVersion;
import com.contractsys.contract.ContractVersionRepository;
import com.contractsys.contract.TaskStatus;
import com.contractsys.contract.TaskType;
import com.contractsys.customer.Customer;
import com.contractsys.customer.CustomerRepository;
import com.contractsys.user.RoleRepository;
import com.contractsys.user.SysRole;
import com.contractsys.user.SysUser;
import com.contractsys.user.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(20)
public class DemoDataInitializer implements CommandLineRunner {
    private static final String DEMO_PREFIX = "demo_";
    private static final String DEMO_PASSWORD = "123456";

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
        cleanupDemoData();
        if (enabled) {
            seedDemoData();
        }
    }

    private void seedDemoData() {
        DemoUsers users = new DemoUsers(
                user("demo_admin", "演示系统管理员", "ROLE_ADMIN", "13900001000", "demo.admin@example.com"),
                user("demo_manager", "演示合同管理员", "ROLE_CONTRACT_ADMIN", "13900001001", "demo.manager@example.com"),
                user("demo_drafter", "演示起草员", "ROLE_OPERATOR", "13900001002", "demo.drafter@example.com"),
                user("demo_countersign", "演示会签员", "ROLE_OPERATOR", "13900001003", "demo.countersign@example.com"),
                user("demo_approver", "演示审批员", "ROLE_OPERATOR", "13900001004", "demo.approver@example.com"),
                user("demo_signer", "演示签订员", "ROLE_OPERATOR", "13900001005", "demo.signer@example.com"),
                user("demo_viewer", "演示新用户", "ROLE_NEW_USER", "13900001006", "demo.viewer@example.com")
        );

        DemoCustomers customers = new DemoCustomers(
                customer("DEMO-CUSTOMER-001", "演示客户A-上海云采", "上海市浦东新区演示路 100 号", "021-60010001", "招商银行上海分行", "6222001000000001"),
                customer("DEMO-CUSTOMER-002", "演示客户B-杭州智造", "杭州市滨江区合同大道 88 号", "0571-60010002", "中国银行杭州分行", "6222001000000002"),
                customer("DEMO-CUSTOMER-003", "演示客户C-成都文旅", "成都市高新区天府三街 66 号", "028-60010003", "建设银行成都分行", "6222001000000003"),
                customer("DEMO-CUSTOMER-004", "演示客户D-深圳物流", "深圳市南山区科技园 18 号", "0755-60010004", "工商银行深圳分行", "6222001000000004")
        );

        seedDraftContract(users, customers);
        seedAssignedContract(users, customers);
        seedCountersignedContract(users, customers);
        seedFinalizedContract(users, customers);
        seedApprovedContract(users, customers);
        seedSignedContract(users, customers);
        seedReturnedContract(users, customers);
        seedRejectedContract(users, customers);
        seedCancelledContract(users, customers);
    }

    private void seedDraftContract(DemoUsers users, DemoCustomers customers) {
        Contract contract = contract("DEMO-CONTRACT-001", "演示采购合同",
                customers.shanghai(), users.drafter(), ContractStatus.DRAFT,
                LocalDate.now().minusDays(3), 6, 1, null, null, null,
                content("设备采购", "草稿已完成基础条款，等待合同管理员分配会签、审批、签订人员。"));
        version(contract, users.drafter(), 1, "演示起草");
        history(contract, null, ContractStatus.DRAFT, users.drafter(), "起草合同");
        task(contract, users.manager(), TaskType.ASSIGN, TaskStatus.PENDING, null, 1, null);
    }

    private void seedAssignedContract(DemoUsers users, DemoCustomers customers) {
        Contract contract = contract("DEMO-CONTRACT-002", "演示-会签进行中-系统集成",
                customers.hangzhou(), users.drafter(), ContractStatus.ASSIGNED,
                LocalDate.now().minusDays(9), 12, 1, null, null, null,
                content("系统集成", "管理员已完成流程分配，当前展示会签员待办和后续审批/签订预分配。"));
        version(contract, users.drafter(), 1, "初稿");
        history(contract, null, ContractStatus.DRAFT, users.drafter(), "起草合同");
        history(contract, ContractStatus.DRAFT, ContractStatus.ASSIGNED, users.manager(), "管理员分配流程人员");
        task(contract, users.manager(), TaskType.ASSIGN, TaskStatus.DONE, "已分配会签、审批、签订人员", 1, LocalDateTime.now().minusDays(8));
        task(contract, users.countersign(), TaskType.COUNTERSIGN, TaskStatus.PENDING, null, 1, null);
        task(contract, users.approver(), TaskType.APPROVAL, TaskStatus.SUPERSEDED, "已分配，等待会签完成后激活", 1, LocalDateTime.now().minusDays(8));
        task(contract, users.signer(), TaskType.SIGN, TaskStatus.SUPERSEDED, "已分配，等待审批完成后激活", 1, LocalDateTime.now().minusDays(8));
    }

    private void seedCountersignedContract(DemoUsers users, DemoCustomers customers) {
        Contract contract = contract("DEMO-CONTRACT-003", "演示-待定稿-年度运维",
                customers.chengdu(), users.drafter(), ContractStatus.COUNTERSIGNED,
                LocalDate.now().minusDays(16), 12, 1, null, null, null,
                content("年度运维", "会签已通过，起草员需要根据意见完成最终稿。"));
        version(contract, users.drafter(), 1, "初稿");
        version(contract, users.drafter(), 2, "根据会签意见补充服务响应条款");
        history(contract, null, ContractStatus.DRAFT, users.drafter(), "起草合同");
        history(contract, ContractStatus.DRAFT, ContractStatus.ASSIGNED, users.manager(), "管理员分配流程人员");
        history(contract, ContractStatus.ASSIGNED, ContractStatus.COUNTERSIGNED, users.countersign(), "全部会签完成");
        task(contract, users.manager(), TaskType.ASSIGN, TaskStatus.DONE, "流程人员已确认", 1, LocalDateTime.now().minusDays(15));
        task(contract, users.countersign(), TaskType.COUNTERSIGN, TaskStatus.DONE, "同意，建议增加故障响应 SLA。", 1, LocalDateTime.now().minusDays(14));
        task(contract, users.drafter(), TaskType.FINALIZE, TaskStatus.PENDING, null, 1, null);
        task(contract, users.approver(), TaskType.APPROVAL, TaskStatus.SUPERSEDED, "已分配，等待定稿后激活", 1, LocalDateTime.now().minusDays(15));
        task(contract, users.signer(), TaskType.SIGN, TaskStatus.SUPERSEDED, "已分配，等待审批后激活", 1, LocalDateTime.now().minusDays(15));
    }

    private void seedFinalizedContract(DemoUsers users, DemoCustomers customers) {
        Contract contract = contract("DEMO-CONTRACT-004", "演示-审批进行中-软件订阅",
                customers.shenzhen(), users.drafter(), ContractStatus.FINALIZED,
                LocalDate.now().minusDays(22), 10, 1, null, null, null,
                content("软件订阅", "合同已定稿，审批员登录后可看到审批待办。"));
        version(contract, users.drafter(), 1, "初稿");
        version(contract, users.drafter(), 2, "定稿版本");
        history(contract, null, ContractStatus.DRAFT, users.drafter(), "起草合同");
        history(contract, ContractStatus.DRAFT, ContractStatus.ASSIGNED, users.manager(), "管理员分配流程人员");
        history(contract, ContractStatus.ASSIGNED, ContractStatus.COUNTERSIGNED, users.countersign(), "全部会签完成");
        history(contract, ContractStatus.COUNTERSIGNED, ContractStatus.FINALIZED, users.drafter(), "起草人定稿");
        task(contract, users.manager(), TaskType.ASSIGN, TaskStatus.DONE, "流程人员已确认", 1, LocalDateTime.now().minusDays(21));
        task(contract, users.countersign(), TaskType.COUNTERSIGN, TaskStatus.DONE, "同意。", 1, LocalDateTime.now().minusDays(20));
        task(contract, users.drafter(), TaskType.FINALIZE, TaskStatus.DONE, "已补充付款节点并定稿", 1, LocalDateTime.now().minusDays(19));
        task(contract, users.approver(), TaskType.APPROVAL, TaskStatus.PENDING, null, 1, null);
        task(contract, users.signer(), TaskType.SIGN, TaskStatus.SUPERSEDED, "已分配，等待审批完成后激活", 1, LocalDateTime.now().minusDays(21));
    }

    private void seedApprovedContract(DemoUsers users, DemoCustomers customers) {
        Contract contract = contract("DEMO-CONTRACT-005", "演示-待签订-物流服务",
                customers.shenzhen(), users.drafter(), ContractStatus.APPROVED,
                LocalDate.now().minusDays(31), 12, 1, null, null, null,
                content("物流服务", "审批已通过，签订员登录后可以录入签订日期和签订说明。"));
        version(contract, users.drafter(), 1, "初稿");
        version(contract, users.drafter(), 2, "审批稿");
        history(contract, null, ContractStatus.DRAFT, users.drafter(), "起草合同");
        history(contract, ContractStatus.DRAFT, ContractStatus.ASSIGNED, users.manager(), "管理员分配流程人员");
        history(contract, ContractStatus.ASSIGNED, ContractStatus.COUNTERSIGNED, users.countersign(), "全部会签完成");
        history(contract, ContractStatus.COUNTERSIGNED, ContractStatus.FINALIZED, users.drafter(), "起草人定稿");
        history(contract, ContractStatus.FINALIZED, ContractStatus.APPROVED, users.approver(), "全部审批通过");
        task(contract, users.manager(), TaskType.ASSIGN, TaskStatus.DONE, "流程人员已确认", 1, LocalDateTime.now().minusDays(30));
        task(contract, users.countersign(), TaskType.COUNTERSIGN, TaskStatus.DONE, "同意。", 1, LocalDateTime.now().minusDays(29));
        task(contract, users.drafter(), TaskType.FINALIZE, TaskStatus.DONE, "已定稿", 1, LocalDateTime.now().minusDays(28));
        task(contract, users.approver(), TaskType.APPROVAL, TaskStatus.DONE, "审批通过，预算匹配。", 1, LocalDateTime.now().minusDays(27));
        task(contract, users.signer(), TaskType.SIGN, TaskStatus.PENDING, null, 1, null);
    }

    private void seedSignedContract(DemoUsers users, DemoCustomers customers) {
        Contract contract = contract("DEMO-CONTRACT-006", "演示-已签订-云资源采购",
                customers.shanghai(), users.drafter(), ContractStatus.SIGNED,
                LocalDate.now().minusDays(65), 24, 1, null, LocalDate.now().minusDays(5),
                "线下盖章完成，合同扫描件已归档，签订金额 860000 元。",
                content("云资源采购", "完整流程已结束，用于展示已签订状态、签订信息和历史轨迹。"));
        version(contract, users.drafter(), 1, "初稿");
        version(contract, users.drafter(), 2, "最终签订稿");
        history(contract, null, ContractStatus.DRAFT, users.drafter(), "起草合同");
        history(contract, ContractStatus.DRAFT, ContractStatus.ASSIGNED, users.manager(), "管理员分配流程人员");
        history(contract, ContractStatus.ASSIGNED, ContractStatus.COUNTERSIGNED, users.countersign(), "全部会签完成");
        history(contract, ContractStatus.COUNTERSIGNED, ContractStatus.FINALIZED, users.drafter(), "起草人定稿");
        history(contract, ContractStatus.FINALIZED, ContractStatus.APPROVED, users.approver(), "全部审批通过");
        history(contract, ContractStatus.APPROVED, ContractStatus.SIGNED, users.signer(), "合同签订完成");
        task(contract, users.manager(), TaskType.ASSIGN, TaskStatus.DONE, "流程人员已确认", 1, LocalDateTime.now().minusDays(60));
        task(contract, users.countersign(), TaskType.COUNTERSIGN, TaskStatus.DONE, "同意。", 1, LocalDateTime.now().minusDays(58));
        task(contract, users.drafter(), TaskType.FINALIZE, TaskStatus.DONE, "已定稿", 1, LocalDateTime.now().minusDays(56));
        task(contract, users.approver(), TaskType.APPROVAL, TaskStatus.DONE, "审批通过。", 1, LocalDateTime.now().minusDays(50));
        task(contract, users.signer(), TaskType.SIGN, TaskStatus.DONE, "线下盖章完成，合同扫描件已归档。", 1, LocalDateTime.now().minusDays(5));
    }

    private void seedReturnedContract(DemoUsers users, DemoCustomers customers) {
        Contract contract = contract("DEMO-CONTRACT-007", "演示-打回修改-咨询服务",
                customers.hangzhou(), users.drafter(), ContractStatus.RETURNED,
                LocalDate.now().minusDays(18), 8, 1, "FINALIZE", null, null,
                content("咨询服务", "审批阶段发现交付成果描述不清，已打回至重新定稿。"));
        version(contract, users.drafter(), 1, "初稿");
        version(contract, users.drafter(), 2, "审批稿");
        history(contract, null, ContractStatus.DRAFT, users.drafter(), "起草合同");
        history(contract, ContractStatus.DRAFT, ContractStatus.ASSIGNED, users.manager(), "管理员分配流程人员");
        history(contract, ContractStatus.ASSIGNED, ContractStatus.COUNTERSIGNED, users.countersign(), "全部会签完成");
        history(contract, ContractStatus.COUNTERSIGNED, ContractStatus.FINALIZED, users.drafter(), "起草人定稿");
        history(contract, ContractStatus.FINALIZED, ContractStatus.RETURNED, users.approver(), "第 1 轮打回至重新定稿");
        task(contract, users.manager(), TaskType.ASSIGN, TaskStatus.DONE, "流程人员已确认", 1, LocalDateTime.now().minusDays(17));
        task(contract, users.countersign(), TaskType.COUNTERSIGN, TaskStatus.DONE, "同意。", 1, LocalDateTime.now().minusDays(16));
        task(contract, users.drafter(), TaskType.FINALIZE, TaskStatus.DONE, "已定稿", 1, LocalDateTime.now().minusDays(15));
        task(contract, users.approver(), TaskType.APPROVAL, TaskStatus.REJECTED, "打回至重新定稿：交付成果需补充验收标准。", 1, LocalDateTime.now().minusDays(14));
        task(contract, users.signer(), TaskType.SIGN, TaskStatus.SUPERSEDED, "合同已被打回，当前轮待办已封存", 1, LocalDateTime.now().minusDays(14));
        task(contract, users.drafter(), TaskType.REVISE, TaskStatus.PENDING, null, 1, null);
    }

    private void seedRejectedContract(DemoUsers users, DemoCustomers customers) {
        Contract contract = contract("DEMO-CONTRACT-008", "演示-审批拒绝-临采协议",
                customers.chengdu(), users.drafter(), ContractStatus.REJECTED,
                LocalDate.now().minusDays(27), 3, 1, null, null, null,
                content("临采协议", "审批被拒绝，起草员进入详情后会确认拒绝通知待办。"));
        version(contract, users.drafter(), 1, "初稿");
        version(contract, users.drafter(), 2, "审批稿");
        history(contract, null, ContractStatus.DRAFT, users.drafter(), "起草合同");
        history(contract, ContractStatus.DRAFT, ContractStatus.ASSIGNED, users.manager(), "管理员分配流程人员");
        history(contract, ContractStatus.ASSIGNED, ContractStatus.COUNTERSIGNED, users.countersign(), "全部会签完成");
        history(contract, ContractStatus.COUNTERSIGNED, ContractStatus.FINALIZED, users.drafter(), "起草人定稿");
        history(contract, ContractStatus.FINALIZED, ContractStatus.REJECTED, users.approver(), "审批拒绝");
        task(contract, users.manager(), TaskType.ASSIGN, TaskStatus.DONE, "流程人员已确认", 1, LocalDateTime.now().minusDays(26));
        task(contract, users.countersign(), TaskType.COUNTERSIGN, TaskStatus.DONE, "同意。", 1, LocalDateTime.now().minusDays(25));
        task(contract, users.drafter(), TaskType.FINALIZE, TaskStatus.DONE, "已定稿", 1, LocalDateTime.now().minusDays(24));
        task(contract, users.approver(), TaskType.APPROVAL, TaskStatus.REJECTED, "预算科目不匹配，拒绝提交。", 1, LocalDateTime.now().minusDays(23));
        task(contract, users.drafter(), TaskType.NOTICE, TaskStatus.PENDING, null, 1, null);
        task(contract, users.signer(), TaskType.SIGN, TaskStatus.SUPERSEDED, "审批已拒绝，签订待办已关闭", 1, LocalDateTime.now().minusDays(23));
    }

    private void seedCancelledContract(DemoUsers users, DemoCustomers customers) {
        Contract contract = contract("DEMO-CONTRACT-009", "演示-已取消-展会执行",
                customers.shanghai(), users.drafter(), ContractStatus.CANCELLED,
                LocalDate.now().minusDays(40), 2, 1, null, null, null,
                content("展会执行", "客户需求取消，合同管理员已终止流程。"));
        version(contract, users.drafter(), 1, "初稿");
        history(contract, null, ContractStatus.DRAFT, users.drafter(), "起草合同");
        history(contract, ContractStatus.DRAFT, ContractStatus.CANCELLED, users.manager(), "取消合同");
        task(contract, users.manager(), TaskType.ASSIGN, TaskStatus.SUPERSEDED, "合同已取消，待办已关闭", 1, LocalDateTime.now().minusDays(39));
    }

    private SysUser user(String username, String displayName, String roleCode, String phone, String email) {
        SysRole role = roleRepository.findByRoleCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Missing role " + roleCode));
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        user.setPhone(phone);
        user.setEmail(email);
        user.getRoles().add(role);
        return userRepository.save(user);
    }

    private Customer customer(String customerNo, String name, String address, String tel,
                              String bankName, String bankAccount) {
        Customer customer = new Customer();
        customer.setCustomerNo(customerNo);
        customer.setName(name);
        customer.setAddress(address);
        customer.setTel(tel);
        customer.setFax(tel.replace("6001", "6002"));
        customer.setPostalCode("200000");
        customer.setBankName(bankName);
        customer.setBankAccount(bankAccount);
        customer.setRemark("演示数据：用于展示客户信息、合同查询和权限范围差异");
        return customerRepository.save(customer);
    }

    private Contract contract(String contractNo, String name, Customer customer, SysUser drafter,
                              ContractStatus status, LocalDate beginDate, int months, int currentRound,
                              String returnTargetStage, LocalDate signedDate, String signInfo, String content) {
        Contract contract = new Contract();
        contract.setContractNo(contractNo);
        contract.setName(name);
        contract.setCustomer(customer);
        contract.setBeginDate(beginDate);
        contract.setEndDate(beginDate.plusMonths(months));
        contract.setContent(content);
        contract.setDrafter(drafter);
        contract.setStatus(status);
        contract.setCurrentRound(currentRound);
        contract.setReturnTargetStage(returnTargetStage);
        contract.setSignedDate(signedDate);
        contract.setSignInfo(signInfo);
        return contractRepository.save(contract);
    }

    private void version(Contract contract, SysUser operator, int versionNo, String remark) {
        ContractVersion version = new ContractVersion();
        version.setContract(contract);
        version.setVersionNo(versionNo);
        version.setName(contract.getName());
        version.setContent(contract.getContent());
        version.setOperator(operator);
        version.setRemark(remark);
        versionRepository.save(version);
    }

    private void history(Contract contract, ContractStatus from, ContractStatus to, SysUser operator, String remark) {
        ContractStateHistory history = new ContractStateHistory();
        history.setContract(contract);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setOperator(operator);
        history.setRemark(remark);
        historyRepository.save(history);
    }

    private void task(Contract contract, SysUser assignee, TaskType type, TaskStatus status,
                      String opinion, int round, LocalDateTime operatedAt) {
        ContractTask task = new ContractTask();
        task.setContract(contract);
        task.setAssignee(assignee);
        task.setTaskType(type);
        task.setTaskStatus(status);
        task.setOpinion(opinion);
        task.setRound(round);
        task.setOperatedAt(operatedAt);
        taskRepository.save(task);
    }

    private String content(String subject, String scenario) {
        return """
                合同主题：%s

                一、业务背景
                %s

                二、主要条款
                1. 乙方按双方确认的范围提供产品或服务。
                2. 甲方按验收节点完成付款，逾期付款按合同约定承担责任。
                3. 双方应对合同执行过程中获得的商业信息承担保密义务。

                三、演示说明
                该合同为系统演示数据，用于展示合同列表、流程详情、版本记录、时间线和我的待办。
                """.formatted(subject, scenario);
    }

    private void cleanupDemoData() {
        List<Long> contractIds = jdbcTemplate.queryForList(
                "select id from contract where contract_no like ? or name like '演示-%' or name = '演示采购合同'",
                Long.class, "DEMO-CONTRACT-%");
        List<Long> userIds = jdbcTemplate.queryForList(
                "select id from sys_user where username like ?", Long.class, DEMO_PREFIX + "%");
        if (!contractIds.isEmpty()) {
            deleteByIds("delete from attachment where contract_id in (%s)", contractIds);
            deleteByIds("delete from contract_version where contract_id in (%s)", contractIds);
            deleteByIds("delete from contract_task where contract_id in (%s)", contractIds);
            deleteByIds("delete from contract_state_history where contract_id in (%s)", contractIds);
            deleteByIds("delete from contract where id in (%s)", contractIds);
        }
        jdbcTemplate.update("delete from customer where customer_no like ? or name like '演示客户%'", "DEMO-CUSTOMER-%");
        jdbcTemplate.update("delete from operation_log where content like '%演示%'");
        if (!userIds.isEmpty()) {
            deleteByIds("delete from operation_log where operator_id in (%s)", userIds);
            deleteByIds("delete from sys_user_role where user_id in (%s)", userIds);
            deleteByIds("delete from sys_user where id in (%s)", userIds);
        }
    }

    private void deleteByIds(String sql, List<Long> ids) {
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        jdbcTemplate.update(String.format(sql, placeholders), ids.toArray());
    }

    private record DemoUsers(SysUser admin,
                             SysUser manager,
                             SysUser drafter,
                             SysUser countersign,
                             SysUser approver,
                             SysUser signer,
                             SysUser viewer) {
    }

    private record DemoCustomers(Customer shanghai,
                                 Customer hangzhou,
                                 Customer chengdu,
                                 Customer shenzhen) {
    }
}
