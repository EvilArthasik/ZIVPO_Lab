INSERT INTO projects (name, description)
VALUES
    ('RBPO Labs', 'Project for laboratory task tracking'),
    ('Course Work', 'Course work preparation')
ON CONFLICT (name) DO NOTHING;

INSERT INTO tags (name, color)
VALUES
    ('backend', '#2563EB'),
    ('urgent', '#DC2626'),
    ('docs', '#16A34A')
ON CONFLICT (name) DO NOTHING;
