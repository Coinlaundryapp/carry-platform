CREATE TABLE review_reviews (
    id              BIGSERIAL PRIMARY KEY,
    laundromat_id   BIGINT NOT NULL,
    customer_id     BIGINT NOT NULL,
    comment         VARCHAR(1000),
    rating          INT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_review_reviews_laundromat_id ON review_reviews(laundromat_id);
CREATE INDEX idx_review_reviews_customer_id ON review_reviews(customer_id);

CREATE TABLE review_media (
    id              BIGSERIAL PRIMARY KEY,
    review_id       BIGINT NOT NULL REFERENCES review_reviews(id) ON DELETE CASCADE,
    media_url       VARCHAR(500) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_review_media_review_id ON review_media(review_id);
