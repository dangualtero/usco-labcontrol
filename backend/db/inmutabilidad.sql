-- Refuerza en MySQL la inmutabilidad del historial (RF-03): impide UPDATE y DELETE sobre historial_evento.
-- Se ejecuta DESPUÉS de que Hibernate cree la tabla (primer arranque del backend); si la tabla aún no existe,
-- vuelve a ejecutar este script manualmente:  mysql -u root -p labcontrol < backend/db/inmutabilidad.sql
DROP TRIGGER IF EXISTS historial_no_update;
DROP TRIGGER IF EXISTS historial_no_delete;
DELIMITER $$
CREATE TRIGGER historial_no_update BEFORE UPDATE ON historial_evento
FOR EACH ROW BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El historial es inmutable: no se permite UPDATE';
END$$
CREATE TRIGGER historial_no_delete BEFORE DELETE ON historial_evento
FOR EACH ROW BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El historial es inmutable: no se permite DELETE';
END$$
DELIMITER ;
