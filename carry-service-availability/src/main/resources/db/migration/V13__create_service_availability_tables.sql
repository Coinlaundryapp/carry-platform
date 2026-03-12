CREATE TABLE service_areas (
    id BIGSERIAL PRIMARY KEY,
    area_code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE service_area_schedules (
    id BIGSERIAL PRIMARY KEY,
    service_area_id BIGINT NOT NULL REFERENCES service_areas(id) ON DELETE CASCADE,
    day_of_week INT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    open_time TIME NOT NULL,
    close_time TIME NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (service_area_id, day_of_week)
);

CREATE TABLE service_area_holidays (
    id BIGSERIAL PRIMARY KEY,
    service_area_id BIGINT NOT NULL REFERENCES service_areas(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    reason VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (service_area_id, date)
);

CREATE INDEX idx_service_areas_status ON service_areas(status);
CREATE INDEX idx_service_area_holidays_date ON service_area_holidays(service_area_id, date);
