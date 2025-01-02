-- change terms to terms_deprecated
ALTER TABLE terms
    RENAME TO terms_deprecated;

-- change term_agrees to term_agrees_deprecated
ALTER TABLE term_agrees
    RENAME TO term_agrees_deprecated;

-- create term_metas table
CREATE TABLE term_metas
(
    id         BIGSERIAL PRIMARY KEY,
    title      VARCHAR(255)              NOT NULL,
    code       VARCHAR(100)              NOT NULL UNIQUE,
    term_type  term_types                NOT NULL,
    created_at date default current_date NOT NULL,
    updated_at date
);

CREATE INDEX idx_term_metas_code ON term_metas (code);
CREATE INDEX idx_term_metas_term_type ON term_metas (term_type);

-- create terms table
CREATE TABLE terms
(
    id            BIGSERIAL PRIMARY KEY,
    term_meta_id  BIGINT                    NOT NULL,
    content       TEXT                      NOT NULL,
    version_count INT  DEFAULT 1            NOT NULL,
    created_at    date default current_date NOT NULL
);

CREATE INDEX idx_terms_term_meta_id ON terms (term_meta_id);
CREATE INDEX idx_terms_created_at_and_version_count ON terms (created_at, version_count);

-- create term_agreements table
CREATE TABLE term_agreements
(
    id         BIGSERIAL PRIMARY KEY,
    term_id    BIGINT                    NOT NULL,
    user_id    BIGINT                    NOT NULL,
    agree_yn   BOOLEAN                   NOT NULL,
    updated_at date default current_date NOT NULL
);

CREATE INDEX idx_term_agreements_term_id ON term_agreements (term_id);
CREATE INDEX idx_term_agreements_user_id ON term_agreements (user_id);