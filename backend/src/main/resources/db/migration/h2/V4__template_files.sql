ALTER TABLE contract_template ADD COLUMN original_name VARCHAR(120);
ALTER TABLE contract_template ADD COLUMN stored_name VARCHAR(120);
ALTER TABLE contract_template ADD COLUMN content_type VARCHAR(100);
ALTER TABLE contract_template ADD COLUMN file_size BIGINT;
