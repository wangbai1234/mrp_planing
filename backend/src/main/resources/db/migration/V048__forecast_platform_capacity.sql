-- Forecast platform copies material.spec_model; preserve the full source capacity.
ALTER TABLE forecast_detail MODIFY COLUMN platform VARCHAR(256) NULL;
