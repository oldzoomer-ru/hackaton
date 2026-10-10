CREATE TABLE users (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    telegram_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(255),
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE hackathons (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    name VARCHAR(255) NOT NULL,
    track VARCHAR(255),
    registration_deadline TIMESTAMP,
    online_stage_start TIMESTAMP,
    online_stage_end TIMESTAMP,
    results_date TIMESTAMP,
    final_stage_start TIMESTAMP,
    final_stage_end TIMESTAMP,
    status VARCHAR(50) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tasks (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    hackathon_id BIGINT NOT NULL REFERENCES hackathons(id),
    title VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'TODO',
    assignee VARCHAR(255),
    deadline TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    notes TEXT
);

CREATE TABLE reminders (
    id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
    telegram_user_id BIGINT NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    text VARCHAR(1000) NOT NULL,
    type VARCHAR(50) NOT NULL,
    executed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
