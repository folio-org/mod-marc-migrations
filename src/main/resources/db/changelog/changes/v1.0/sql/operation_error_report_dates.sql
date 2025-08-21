CREATE OR REPLACE FUNCTION set_error_report_dates() RETURNS trigger AS
$$
BEGIN
    IF NEW.status = 'IN_PROGRESS'
    THEN
        NEW.started_at = now();
    END IF;

    IF NEW.status = 'COMPLETED'
    THEN
        NEW.finished_at = now();
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS operation_error_report_dates ON operation_error_report;

CREATE TRIGGER operation_error_report_dates
    BEFORE UPDATE
    ON operation_error_report
    FOR EACH ROW
EXECUTE PROCEDURE set_error_report_dates();