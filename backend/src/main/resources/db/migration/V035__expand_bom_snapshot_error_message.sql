-- BOM source validation can produce thousands of actionable errors. Keep the failed
-- snapshot durable instead of rolling the transaction back because a TEXT column overflows.
ALTER TABLE bom_snapshot
    MODIFY COLUMN error_message MEDIUMTEXT NULL COMMENT 'BOM snapshot validation summary when status=FAILED';
