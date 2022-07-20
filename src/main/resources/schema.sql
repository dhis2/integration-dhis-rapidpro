CREATE TABLE IF NOT EXISTS DEAD_LETTER_CHANNEL (
    id                INTEGER                             NOT NULL PRIMARY KEY AUTO_INCREMENT,
    payload           VARCHAR                             NOT NULL,
    error_message     VARCHAR                             NOT NULL,
    status            ENUM('PROCESSED', 'RETRY', 'ERROR') NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_processed_at TIMESTAMP WITH TIME ZONE            NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ON DEAD_LETTER_CHANNEL(status);