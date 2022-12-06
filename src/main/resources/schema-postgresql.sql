CREATE TABLE IF NOT EXISTS DEAD_LETTER_CHANNEL (
    id                      BIGSERIAL                   PRIMARY KEY,
    payload                 VARCHAR                     NOT NULL,
    data_set_code           VARCHAR                     NOT NULL,
    report_period_offset    INTEGER                     NOT NULL,
    organisation_unit_id    VARCHAR,
    error_message           VARCHAR                     NOT NULL,
    status                  VARCHAR                     NOT NULL CHECK (status = 'PROCESSED' OR status = 'RETRY' OR status = 'ERROR'),
    created_at              TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_processed_at       TIMESTAMP WITH TIME ZONE    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ON DEAD_LETTER_CHANNEL(status);

CREATE TABLE IF NOT EXISTS POLLER (
    flow_uuid    VARCHAR                   PRIMARY KEY,
    last_run_at  TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS TOKEN (
    value_  VARCHAR     PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS SUCCESS_LOG (
    id                      BIGSERIAL                           PRIMARY KEY,
    dhis_request            VARCHAR                             NOT NULL,
    dhis_response           VARCHAR                             NOT NULL,
    rapidpro_payload        VARCHAR                             NOT NULL,
    data_set_code           VARCHAR                             NOT NULL,
    report_period_offset    INTEGER                             NOT NULL,
    organisation_unit_id    VARCHAR,
    created_at              TIMESTAMP WITH TIME ZONE            NOT NULL DEFAULT CURRENT_TIMESTAMP
);