-- =====================================================================================================
-- USCO LabControl · 02 · Historial inmutable (RF-03)
-- Triggers que rechazan cualquier UPDATE o DELETE sobre historial_eventos.
-- Ejecutar como root. Si MySQL tiene el binlog activo y no eres root, inicia el servidor con
-- --log-bin-trust-function-creators=1 (iniciar-mysql.ps1 y docker-compose ya lo hacen).
-- No usa DELIMITER: funciona igual en la consola de mysql, en IntelliJ y en Docker.
-- =====================================================================================================
SET NAMES utf8mb4;
USE labcontrol;

DROP TRIGGER IF EXISTS historial_no_update;
DROP TRIGGER IF EXISTS historial_no_delete;

CREATE TRIGGER historial_no_update BEFORE UPDATE ON historial_eventos
  FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El historial es inmutable: no se permite UPDATE';

CREATE TRIGGER historial_no_delete BEFORE DELETE ON historial_eventos
  FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El historial es inmutable: no se permite DELETE';
