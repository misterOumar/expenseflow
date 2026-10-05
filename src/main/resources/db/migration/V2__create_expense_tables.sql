CREATE TABLE expense_reports (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id         BIGINT        NOT NULL,
    title            VARCHAR(255)  NOT NULL,
    period_month     INTEGER       NOT NULL,
    period_year      INTEGER       NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    total_amount     NUMERIC(12,2) NOT NULL DEFAULT 0,
    submitted_at     TIMESTAMPTZ,
    decided_at       TIMESTAMPTZ,
    decided_by       BIGINT,
    rejection_reason VARCHAR(1000),
    version          BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT fk_reports_owner      FOREIGN KEY (owner_id)   REFERENCES users (id),
    CONSTRAINT fk_reports_decided_by FOREIGN KEY (decided_by) REFERENCES users (id),
    CONSTRAINT uq_reports_owner_period UNIQUE (owner_id, period_month, period_year),
    CONSTRAINT chk_reports_month  CHECK (period_month BETWEEN 1 AND 12),
    CONSTRAINT chk_reports_year   CHECK (period_year BETWEEN 2000 AND 2100),
    CONSTRAINT chk_reports_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PAID')),
    CONSTRAINT chk_reports_total  CHECK (total_amount >= 0)
);

CREATE INDEX idx_reports_status ON expense_reports (status);

CREATE TABLE receipts (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    stored_key        VARCHAR(64)  NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    sha256            VARCHAR(64)  NOT NULL,
    uploaded_by       BIGINT       NOT NULL,
    uploaded_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT fk_receipts_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES users (id),
    CONSTRAINT uq_receipts_stored_key UNIQUE (stored_key),
    CONSTRAINT chk_receipts_content_type CHECK (content_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT chk_receipts_size CHECK (size_bytes > 0 AND size_bytes <= 5242880)
);

CREATE TABLE expense_lines (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id   BIGINT        NOT NULL,
    spent_on    DATE          NOT NULL,
    category    VARCHAR(20)   NOT NULL,
    description VARCHAR(500)  NOT NULL,
    amount_ht   NUMERIC(12,2) NOT NULL,
    vat_amount  NUMERIC(12,2) NOT NULL,
    amount_ttc  NUMERIC(12,2) NOT NULL,
    receipt_id  BIGINT,

    CONSTRAINT fk_lines_report  FOREIGN KEY (report_id)  REFERENCES expense_reports (id) ON DELETE CASCADE,
    CONSTRAINT fk_lines_receipt FOREIGN KEY (receipt_id) REFERENCES receipts (id),
    CONSTRAINT uq_lines_receipt UNIQUE (receipt_id),
    CONSTRAINT chk_lines_category CHECK (category IN ('MEAL', 'TRANSPORT', 'ACCOMMODATION', 'SUPPLIES', 'OTHER')),
    CONSTRAINT chk_lines_amounts  CHECK (amount_ht >= 0 AND vat_amount >= 0 AND amount_ttc = amount_ht + vat_amount)
);

CREATE INDEX idx_lines_report_id ON expense_lines (report_id);

CREATE TABLE status_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id   BIGINT        NOT NULL,
    from_status VARCHAR(20)   NOT NULL,
    to_status   VARCHAR(20)   NOT NULL,
    actor_id    BIGINT        NOT NULL,
    at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    comment     VARCHAR(1000),

    CONSTRAINT fk_history_report FOREIGN KEY (report_id) REFERENCES expense_reports (id) ON DELETE CASCADE,
    CONSTRAINT fk_history_actor  FOREIGN KEY (actor_id)  REFERENCES users (id)
);

CREATE INDEX idx_history_report_id ON status_history (report_id, at);