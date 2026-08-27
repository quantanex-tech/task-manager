CREATE TABLE IF NOT EXISTS infrastructure_probe (
    id uuid PRIMARY KEY,
    label text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS infrastructure_probe_created_at_idx
    ON infrastructure_probe(created_at);
