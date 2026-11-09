-- Repair a known UAT fixture that was double-encoded before the UTF-8 connection
-- settings were standardized. The exact predicate avoids changing legitimate
-- historical calendar reasons.
UPDATE production_calendar_day
SET reason = CONVERT(CAST(CONVERT(reason USING latin1) AS BINARY) USING utf8mb4)
WHERE reason = 'UATå…¨ç”Ÿäº§æ—¥';
