-- C025: keep historical issue identity and decisions; new records carry factory scope.
-- Historical multi-factory records cannot safely be assigned to a factory.
ALTER TABLE mrp_decision_issue
    ADD COLUMN factory_code VARCHAR(64) NULL AFTER batch_id;

CREATE INDEX idx_decision_issue_batch_factory_material
    ON mrp_decision_issue(batch_id, factory_code, scope_type, scope_key, status);
