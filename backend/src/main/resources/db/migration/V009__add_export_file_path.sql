-- Add file_path column to export_task table
ALTER TABLE export_task ADD COLUMN file_path VARCHAR(500) AFTER result_resource_id;
