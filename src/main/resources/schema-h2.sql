ALTER TABLE IF EXISTS DEAD_LETTER_CHANNEL RENAME TO REPORT_DEAD_LETTER_CHANNEL;

CREATE TABLE IF NOT EXISTS REPORT_DEAD_LETTER_CHANNEL (
    id                      INTEGER                             PRIMARY KEY AUTO_INCREMENT,
    payload                 VARCHAR                             NOT NULL,
    data_set_code           VARCHAR,
    report_period_offset    INTEGER                             NOT NULL,
    organisation_unit_id    VARCHAR,
    error_message           VARCHAR                             NOT NULL,
    status                  ENUM('PROCESSED', 'RETRY', 'ERROR') NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_processed_at       TIMESTAMP WITH TIME ZONE            NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ON REPORT_DEAD_LETTER_CHANNEL(status);

CREATE TABLE IF NOT EXISTS EVENT_DEAD_LETTER_CHANNEL (
    id                      INTEGER                             PRIMARY KEY AUTO_INCREMENT,
    payload                 VARCHAR                             NOT NULL,
    event_id                VARCHAR                             NOT NULL,
    error_message           VARCHAR                             NOT NULL,
    status                  ENUM('PROCESSED', 'RETRY', 'ERROR') NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_processed_at       TIMESTAMP WITH TIME ZONE            NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ON EVENT_DEAD_LETTER_CHANNEL(status);

CREATE TABLE IF NOT EXISTS POLLER (
    flow_uuid    VARCHAR                   PRIMARY KEY,
    last_run_at  TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS TOKEN (
    value_  VARCHAR PRIMARY KEY
);

ALTER TABLE IF EXISTS SUCCESS_LOG RENAME TO REPORT_SUCCESS_LOG;

CREATE TABLE IF NOT EXISTS REPORT_SUCCESS_LOG (
    id                      INTEGER                             PRIMARY KEY AUTO_INCREMENT,
    dhis_request            VARCHAR                             NOT NULL,
    dhis_response           VARCHAR                             NOT NULL,
    rapidpro_payload        VARCHAR                             NOT NULL,
    data_set_code           VARCHAR                             NOT NULL,
    report_period_offset    INTEGER                             NOT NULL,
    organisation_unit_id    VARCHAR,
    created_at              TIMESTAMP WITH TIME ZONE            NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS EVENT_SUCCESS_LOG (
    id                      INTEGER                             PRIMARY KEY AUTO_INCREMENT,
    dhis_request            VARCHAR                             NOT NULL,
    dhis_response           VARCHAR                             NOT NULL,
    rapidpro_payload        VARCHAR                             NOT NULL,
    event_id                VARCHAR                             NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE            NOT NULL DEFAULT CURRENT_TIMESTAMP
);
