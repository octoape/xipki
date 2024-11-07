DROP TABLE IF EXISTS DBSCHEMA;
DROP TABLE IF EXISTS AUDIT;
DROP TABLE IF EXISTS INTEGRITY;

-- changeset xipki:1
CREATE TABLE DBSCHEMA (
    NAME VARCHAR(45) NOT NULL,
    VALUE2 VARCHAR(100) NOT NULL,
    CONSTRAINT PK_DBSCHEMA PRIMARY KEY (NAME)
);

COMMENT ON TABLE DBSCHEMA IS 'database schema information';

INSERT INTO DBSCHEMA (NAME, VALUE2) VALUES ('VENDOR', 'XIPKI');
INSERT INTO DBSCHEMA (NAME, VALUE2) VALUES ('VERSION', '1');
INSERT INTO DBSCHEMA (NAME, VALUE2) VALUES ('MAX_MESSAGE_LEN', '1000');

CREATE TABLE AUDIT (
    SHARD_ID SMALLINT NOT NULL,
    ID BIGINT NOT NULL,
    TIME CHAR(23) NOT NULL,
    LEVEL VARCHAR(5) NOT NULL,
    EVENT_TYPE SMALLINT NOT NULL,
    PREVIOUS_ID BIGINT NOT NULL,
    MESSAGE VARCHAR(1000) NOT NULL,
    TAG VARCHAR(100) NOT NULL
);

COMMENT ON COLUMN AUDIT.TIME IS 'Logging time e.g. 2022.03.06-10:18:35.483';
COMMENT ON COLUMN AUDIT.LEVEL IS 'DEBUG,INFO,ERROR';
COMMENT ON COLUMN AUDIT.EVENT_TYPE IS '1 for AuditEvent, 2 for PCIAuditEvent';
COMMENT ON COLUMN AUDIT.PREVIOUS_ID IS 'ID of the previous audit entry. 0 if none.';
COMMENT ON COLUMN AUDIT.MESSAGE IS 'log message';
COMMENT ON COLUMN AUDIT.TAG IS '[algo]:[keyId]:[base64(tag)]';

CREATE TABLE INTEGRITY (
    ID SMALLINT NOT NULL,
    TEXT VARCHAR(1000) NOT NULL
);

INSERT INTO INTEGRITY (ID, TEXT) VALUES ('1', '');

