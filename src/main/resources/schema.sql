CREATE TABLE IF NOT EXISTS DLQ (
    id      INTEGER                           NOT NULL PRIMARY KEY AUTO_INCREMENT,
    payload VARCHAR                           NOT NULL,
    status  ENUM('PROCESSED', 'RETRY', 'ERROR') NOT NULL
);