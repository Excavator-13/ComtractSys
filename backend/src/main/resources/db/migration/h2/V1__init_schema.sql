CREATE TABLE sys_permission (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  permission_code VARCHAR(80) NOT NULL,
  permission_name VARCHAR(40) NOT NULL,
  module VARCHAR(40) NOT NULL,
  url VARCHAR(200),
  description VARCHAR(100),
  CONSTRAINT uk_sys_permission_code UNIQUE (permission_code)
);

CREATE TABLE sys_role (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_code VARCHAR(50) NOT NULL,
  role_name VARCHAR(40) NOT NULL,
  description VARCHAR(100),
  CONSTRAINT uk_sys_role_code UNIQUE (role_code)
);

CREATE TABLE sys_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(40) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  display_name VARCHAR(40),
  phone VARCHAR(20),
  email VARCHAR(100),
  status VARCHAR(20) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  deleted BOOLEAN NOT NULL,
  CONSTRAINT uk_sys_user_username UNIQUE (username)
);

CREATE TABLE sys_role_permission (
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
  CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
);

CREATE TABLE sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id),
  CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
  CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
);

CREATE TABLE customer (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_no VARCHAR(20) NOT NULL,
  name VARCHAR(40) NOT NULL,
  address VARCHAR(100) NOT NULL,
  tel VARCHAR(20) NOT NULL,
  fax VARCHAR(255),
  postal_code VARCHAR(255),
  bank_name VARCHAR(255),
  bank_account VARCHAR(255),
  remark VARCHAR(255),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  deleted BOOLEAN NOT NULL,
  CONSTRAINT uk_customer_no UNIQUE (customer_no)
);

CREATE TABLE contract (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_no VARCHAR(20) NOT NULL,
  name VARCHAR(40) NOT NULL,
  customer_id BIGINT NOT NULL,
  begin_date DATE NOT NULL,
  end_date DATE NOT NULL,
  content TEXT NOT NULL,
  drafter_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  signed_date DATE,
  sign_info TEXT,
  version INT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  deleted BOOLEAN NOT NULL,
  CONSTRAINT uk_contract_no UNIQUE (contract_no),
  CONSTRAINT fk_contract_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
  CONSTRAINT fk_contract_drafter FOREIGN KEY (drafter_id) REFERENCES sys_user (id)
);

CREATE TABLE contract_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  task_type VARCHAR(30) NOT NULL,
  task_status VARCHAR(30) NOT NULL,
  assignee_id BIGINT NOT NULL,
  opinion TEXT,
  operated_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_contract_task_contract FOREIGN KEY (contract_id) REFERENCES contract (id),
  CONSTRAINT fk_contract_task_assignee FOREIGN KEY (assignee_id) REFERENCES sys_user (id)
);

CREATE TABLE contract_state_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  from_status VARCHAR(255),
  to_status VARCHAR(255) NOT NULL,
  operator_id BIGINT NOT NULL,
  remark VARCHAR(255),
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_contract_history_contract FOREIGN KEY (contract_id) REFERENCES contract (id),
  CONSTRAINT fk_contract_history_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id)
);

CREATE TABLE attachment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  contract_id BIGINT NOT NULL,
  original_name VARCHAR(255) NOT NULL,
  stored_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(255) NOT NULL,
  file_size BIGINT NOT NULL,
  uploader_id BIGINT NOT NULL,
  uploaded_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_attachment_contract FOREIGN KEY (contract_id) REFERENCES contract (id),
  CONSTRAINT fk_attachment_uploader FOREIGN KEY (uploader_id) REFERENCES sys_user (id)
);

CREATE TABLE operation_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  operator_id BIGINT,
  operator_name VARCHAR(40),
  module VARCHAR(40) NOT NULL,
  action VARCHAR(40) NOT NULL,
  target_type VARCHAR(40),
  target_id BIGINT,
  content TEXT NOT NULL,
  ip VARCHAR(64),
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT fk_operation_log_operator FOREIGN KEY (operator_id) REFERENCES sys_user (id)
);

CREATE INDEX idx_customer_name_deleted ON customer (name, deleted);
CREATE INDEX idx_contract_status_deleted ON contract (status, deleted);
CREATE INDEX idx_contract_task_assignee_status ON contract_task (assignee_id, task_status);
CREATE INDEX idx_contract_history_created_at ON contract_state_history (created_at);
CREATE INDEX idx_operation_log_created_at ON operation_log (created_at);
CREATE INDEX idx_operation_log_module ON operation_log (module);
