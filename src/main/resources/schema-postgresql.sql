ALTER TABLE IF EXISTS SUCCESS_LOG RENAME TO REPORT_SUCCESS_LOG;

CREATE TABLE IF NOT EXISTS MESSAGE_STORE (
    id              BIGSERIAL PRIMARY KEY,
    key_            VARCHAR NOT NULL,
    headers         VARCHAR NOT NULL,
    body            VARCHAR,
    context         VARCHAR,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS POLLER (
    flow_uuid    VARCHAR                   PRIMARY KEY,
    last_run_at  TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS TOKEN (
    value_  VARCHAR     PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS REPORT_SUCCESS_LOG (
    id                      BIGSERIAL                           PRIMARY KEY,
    dhis_request            VARCHAR                             NOT NULL,
    dhis_response           VARCHAR                             NOT NULL,
    rapidpro_payload        VARCHAR                             NOT NULL,
    data_set_code           VARCHAR                             NOT NULL,
    report_period_offset    INTEGER                             NOT NULL,
    organisation_unit_id    VARCHAR,
    created_at              TIMESTAMP WITH TIME ZONE            NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS EVENT_SUCCESS_LOG (
    id                      BIGSERIAL                           PRIMARY KEY,
    dhis_request            VARCHAR                             NOT NULL,
    dhis_response           VARCHAR                             NOT NULL,
    rapidpro_payload        VARCHAR                             NOT NULL,
    event_id                VARCHAR                             NOT NULL,
    created_at              TIMESTAMP WITH TIME ZONE            NOT NULL DEFAULT CURRENT_TIMESTAMP
);
