-- Soft-delete flag for business-week versions. Existing foreign keys stay:
-- detail rows cascade only on a physical delete, and batch scopes restrict it.
-- Application code marks drafts deleted instead of removing the row.

ALTER TABLE business_week_version
    ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0 AFTER published_at,
    ADD COLUMN deleted_at DATETIME(3) NULL AFTER is_deleted;
