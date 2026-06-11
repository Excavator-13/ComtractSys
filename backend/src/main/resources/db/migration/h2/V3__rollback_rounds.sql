ALTER TABLE contract ADD COLUMN current_round INT NOT NULL DEFAULT 1;
ALTER TABLE contract ADD COLUMN return_target_stage VARCHAR(30);
ALTER TABLE contract_task ADD COLUMN round INT NOT NULL DEFAULT 1;

CREATE INDEX idx_contract_task_round ON contract_task (contract_id, round);
