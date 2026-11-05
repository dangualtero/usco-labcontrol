-- =====================================================================================================
-- USCO LabControl · 04 · Permitir contraseñas normales en los INSERT manuales
-- Solo para bases creadas antes de este cambio (las nuevas ya no tienen la regla). Ejecutar una vez.
-- Después de esto puedes escribir la contraseña normal en password_hash; el backend la cifra con BCrypt
-- a los pocos segundos (o en el primer inicio de sesión).
-- =====================================================================================================
USE labcontrol;
ALTER TABLE usuarios DROP CHECK ck_usuarios_bcrypt;
