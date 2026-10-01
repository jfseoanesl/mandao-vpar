-- AC-001-12: evidencia de qué versión de los términos aceptó cada usuario y cuándo.
ALTER TABLE users ADD COLUMN terms_version varchar(10);
ALTER TABLE users ADD COLUMN terms_accepted_at timestamptz;
