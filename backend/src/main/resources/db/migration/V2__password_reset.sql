-- AC-001-11: tras un restablecimiento por el admin, el usuario debe cambiar su contraseña al entrar.
ALTER TABLE users ADD COLUMN must_change_password boolean NOT NULL DEFAULT false;
