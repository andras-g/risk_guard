-- Spring Modulith Event Publication Registry
CREATE TABLE event_publication (
    id UUID PRIMARY KEY,
    completion_date TIMESTAMPTZ,
    event_type VARCHAR(512) NOT NULL,
    listener_id VARCHAR(512) NOT NULL,
    publication_date TIMESTAMPTZ NOT NULL,
    serialized_event VARCHAR(4000) NOT NULL,
    completion_attempts INT NOT NULL DEFAULT 0,
    last_resubmission_date TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'
);
