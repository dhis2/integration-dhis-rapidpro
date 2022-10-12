CREATE TABLE IF NOT EXISTS DEAD_LETTER_CHANNEL (
    id                      INTEGER                             NOT NULL PRIMARY KEY AUTO_INCREMENT,
    payload                 VARCHAR                             NOT NULL,
    data_set_code           VARCHAR                             NOT NULL,
    report_period_offset    INTEGER                             NOT NULL,
    organisation_unit_id    VARCHAR,
    error_message           VARCHAR                             NOT NULL,
    status                  ENUM('PROCESSED', 'RETRY', 'ERROR') NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_processed_at       TIMESTAMP WITH TIME ZONE            NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ON DEAD_LETTER_CHANNEL(status);

CREATE TABLE IF NOT EXISTS POLLER (
    flow_uuid    VARCHAR                   NOT NULL PRIMARY KEY,
    last_run_at  TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS TOKEN (
    value_  VARCHAR   NOT NULL PRIMARY KEY
);