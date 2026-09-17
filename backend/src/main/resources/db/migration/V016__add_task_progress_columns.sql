-- V016: Add progress tracking columns to calc_task
ALTER TABLE calc_task
    ADD COLUMN phase VARCHAR(50) DEFAULT NULL COMMENT '当前阶段: LOADING/EXPLODING_BOM/CALCULATING/WRITING/SUCCEEDED/FAILED' AFTER error_message,
    ADD COLUMN success_roots INT DEFAULT 0 COMMENT '成功处理的整机数' AFTER phase,
    ADD COLUMN failed_roots INT DEFAULT 0 COMMENT '失败的整机数' AFTER success_roots;
