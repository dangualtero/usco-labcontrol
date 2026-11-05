-- =====================================================================================================
-- USCO LabControl · 00 · Base de datos y usuario de la aplicación
-- Ejecutar UNA sola vez como root (o un administrador de MySQL).
-- La aplicación solo necesita leer y escribir datos: la estructura la crean los scripts 01 y 02.
-- =====================================================================================================
CREATE DATABASE IF NOT EXISTS labcontrol CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'labcontrol'@'localhost' IDENTIFIED BY 'LabControl#2026';
CREATE USER IF NOT EXISTS 'labcontrol'@'%'         IDENTIFIED BY 'LabControl#2026';

-- Mínimo privilegio: solo datos (sin CREATE, ALTER ni DROP). El backend ya no modifica la estructura.
GRANT SELECT, INSERT, UPDATE, DELETE ON labcontrol.* TO 'labcontrol'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON labcontrol.* TO 'labcontrol'@'%';
FLUSH PRIVILEGES;
