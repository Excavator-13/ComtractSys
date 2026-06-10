CREATE TABLE contract_template (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(60) NOT NULL,
  description VARCHAR(100),
  content CLOB NOT NULL,
  enabled BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE contract_version (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  name VARCHAR(40) NOT NULL,
  content CLOB NOT NULL,
  operator_id BIGINT NOT NULL,
  remark VARCHAR(100),
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_contract_version_contract FOREIGN KEY (contract_id) REFERENCES contract (id),
  CONSTRAINT fk_contract_version_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id),
  CONSTRAINT uk_contract_version_no UNIQUE (contract_id, version_no)
);

INSERT INTO contract_template (name, description, content, enabled, created_at, updated_at)
VALUES
('采购合同模板', '适用于普通采购业务',
'采购合同

甲方：
乙方：

一、采购标的
二、合同金额
三、交付与验收
四、付款方式
五、违约责任
六、争议解决', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('服务合同模板', '适用于服务外包或咨询业务',
'服务合同

甲方：
乙方：

一、服务内容
二、服务期限
三、服务费用
四、交付成果
五、保密条款
六、违约责任', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
