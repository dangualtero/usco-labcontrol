-- =====================================================================================================
-- USCO LabControl · 03 · Datos iniciales
-- Sede Neiva, cuentas de acceso y el inventario de los laboratorios. Contraseñas cifradas con BCrypt
-- (las contraseñas en texto plano están en el README). ⚠ En un servidor público, cámbialas o desactiva
-- estas cuentas después del primer ingreso.
-- Se puede ejecutar varias veces: INSERT IGNORE no duplica filas existentes.
-- =====================================================================================================
SET NAMES utf8mb4;
USE labcontrol;

-- ---------------- Sede principal
INSERT IGNORE INTO sedes (id, slug, nombre, ciudad, direccion, activa, creada_en) VALUES
  (1, 'neiva', 'Sede Neiva', 'Neiva', 'Av. Pastrana Borrero, Cra. 1', TRUE, NOW(6));

-- ---------------- Usuarios (rol, sede y contraseña BCrypt)
-- El Coordinador es general: sede_id NULL.
INSERT IGNORE INTO usuarios (nombre, correo, password_hash, rol, documento, codigo_estudiantil, programa, activo, sede_id) VALUES
  ('Administrador LabControl', 'admin@usco.edu.co', '$2a$10$F7MVvg27OZV7KgFN7kd.YemaH7rf9UxvoM2qe2ivnGu10UcvmTjDC', 'COORDINADOR', '1000000001', NULL, 'Coordinación de Laboratorios', TRUE, NULL),  -- Admin#USCO2026
  ('Sandra Milena Vargas', 'coordinador@usco.edu.co', '$2a$10$7V8LnTBq0x45HZ.vrpA0BOSs/zeqz19mcsOjpVSDSNWuzDzmSW0yO', 'COORDINADOR', '36500100', NULL, 'Coordinación de Laboratorios', TRUE, NULL),  -- Coord#USCO2026
  ('Daniel Felipe Gualtero', 'daniel.gualtero@usco.edu.co', '$2a$10$uIq3VrcT4AokbN80ldF8zOIpGyWP3li0HlX6Ik2awsw/ReKUxxOaG', 'COORDINADOR', '1075100001', NULL, 'Ingeniería de Software', TRUE, NULL),  -- Daniel#USCO2026
  ('Carlos David Neira Vega', 'carlos.neira@usco.edu.co', '$2a$10$xWcWbUGWfUbGVk5d/FZM4.XaDUbND0fyYVu/AZJNyUBr1v5JlY7RC', 'AUXILIAR', '1075100002', NULL, 'Ingeniería de Software', TRUE, 1),  -- Carlos#USCO2026
  ('Cesar Sneider Pimiento Torres', 'cesar.pimiento@usco.edu.co', '$2a$10$tbI4j.tU1gaFsjvGgJgOtOhhtenqOOQQ4uT0Vh5Psca9iwU4e9Ak2', 'ESTUDIANTE', '1075100003', '20221100003', 'Ingeniería de Software', TRUE, 1),  -- Cesar#USCO2026
  ('Juan Pablo Trujillo', 'auxiliar@usco.edu.co', '$2a$10$OU4p2f2SZYPyjibDW2d11eHUAqUxSM4eoigHEFwkB9dFZtrQdobiW', 'AUXILIAR', '1075300101', NULL, 'Ing. Electrónica', TRUE, 1),  -- Aux#USCO2026
  ('Marcela Ortiz', 'auxiliar2@usco.edu.co', '$2a$10$e/SB9Z.noNfPHwiEC4zn1u6Fa3tksGqyGvJCg3GCut7bcHCj00YLq', 'AUXILIAR', '1075300102', NULL, 'Ing. Civil', TRUE, 1),  -- Aux2#USCO2026
  ('Mauricio Perdomo', 'docente@usco.edu.co', '$2a$10$PpPlmeNyLBZolz5VKGSy5uEErpEDthB5p31QVTPt0Itv9/RoJdw/.', 'DOCENTE', '36600200', NULL, 'Ing. Agrícola', TRUE, 1),  -- Doc#USCO2026
  ('Laura Gómez', 'estudiante@usco.edu.co', '$2a$10$oeGJ05.PFnLUgbfShiqM0OHvOz3R1rUee5I803nVoWZPHyqsxa8K.', 'ESTUDIANTE', '1075123456', '20211100456', 'Ing. Electrónica', TRUE, 1),  -- Est#USCO2026
  ('Andrés Ramírez', 'andres.ramirez@usco.edu.co', '$2a$10$Wd3jZucFFdQ99FKO6tWuh.j0Dbo7HGMFGlf5PZS5SER4b.LmSEJve', 'ESTUDIANTE', '1075200011', '20201100011', 'Ing. Civil', TRUE, 1),  -- Andres#USCO2026
  ('Valentina Cuéllar', 'valentina.cuellar@usco.edu.co', '$2a$10$bUvikUGGOxbvvoSi4MCNBeAZOnUkEKa1kUCQQvHNX9nrFiIumkqWS', 'ESTUDIANTE', '1075200012', '20211100012', 'Ing. de Petróleos', TRUE, 1),  -- Valentina#USCO2026
  ('Camila Rojas', 'camila.rojas@usco.edu.co', '$2a$10$ZLiwj.PPMbhAogYrno9vsOwjNDmHRkvz8kJicAabfaEQsgmXfYR6G', 'ESTUDIANTE', '1075200013', '20221100013', 'Ing. Agrícola', TRUE, 1),  -- Camila#USCO2026
  ('Sebastián Medina', 'sebastian.medina@usco.edu.co', '$2a$10$TwVdcMAxfCpR2HddDsMAeufoOGcod5aWivOA6rObkGKZOXqd8FeX.', 'ESTUDIANTE', '1075200014', '20211100014', 'Ing. Electrónica', TRUE, 1);  -- Sebastian#USCO2026

-- ---------------- Inventario de la Sede Neiva
-- Series y ubicaciones son datos de referencia: reemplázalos por los del inventario físico.
INSERT IGNORE INTO equipos (placa, nombre, marca, modelo, numero_serie, categoria, laboratorio, ubicacion, descripcion, specs, accesorios,
                            estado_fisico, foto_url, estado, activo, baja_motivo, baja_fecha, sede_id) VALUES
  ('USCO-EQ-100', 'Fuente de Poder DC Programable', 'Rigol', 'DP832', 'DP8A242100101', 'Fuentes y generadores', 'Electrónica',
   'Bloque de Ingeniería · Laboratorio de Electrónica · Mesa 1',
   'Fuente lineal de 3 canales para alimentar prototipos y circuitos de práctica.',
   '3 canales · 30 V / 3 A|Resolución 1 mV / 1 mA|Protección OVP/OCP',
   'Cable de poder|Juego de cables banana-banana|Manual rápido', 'Excelente', '/images/equipos/usco-eq-100.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-101', 'Generador de Funciones', 'Rigol', 'DG1022Z', 'DG1Z242200202', 'Fuentes y generadores', 'Electrónica',
   'Bloque de Ingeniería · Laboratorio de Electrónica · Mesa 1',
   'Generador arbitrario de dos canales para señales senoidales, cuadradas y de pulso.',
   '2 canales · 25 MHz|Muestreo 200 MSa/s|Modulación AM/FM/PM',
   'Cable de poder|2 cables BNC-BNC|Cable USB', 'Bueno', '/images/equipos/usco-eq-101.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-102', 'Osciloscopio Digital Tektronix', 'Tektronix', 'TBS1052B', 'C0192210302', 'Osciloscopios', 'Electrónica',
   'Bloque de Ingeniería · Laboratorio de Electrónica · Mesa 2',
   'Osciloscopio digital de almacenamiento de 2 canales para análisis de señales en el dominio del tiempo.',
   '50 MHz · 2 canales|Muestreo 1 GS/s|Pantalla TFT 7 pulgadas',
   'Cable de poder|2 puntas de prueba P2220|Cable USB', 'Bueno', '/images/equipos/usco-eq-102.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-103', 'Multímetro Fluke 179', 'Fluke', '179', '44830015', 'Multímetros', 'Electrónica',
   'Bloque de Ingeniería · Laboratorio de Electrónica · Mesa 2',
   'Multímetro digital True-RMS con medición de temperatura y capacitancia.',
   'True-RMS 6000 cuentas|CAT III 1000 V / CAT IV 600 V|Temperatura y capacitancia',
   'Puntas de prueba rojo/negro|Termopar tipo K|Estuche', 'Excelente', '/images/equipos/usco-eq-103.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-104', 'Estación de Soldadura', 'Hakko', 'FX-888D', 'FX8D-0415', 'Soldadura', 'Electrónica',
   'Bloque de Ingeniería · Laboratorio de Electrónica · Mesa 4',
   'Estación digital con control de temperatura para ensamble y reparación de circuitos.',
   '70 W|Rango 200–480 °C|Control digital de temperatura',
   'Cable de poder|Soporte con esponja|Punta de repuesto', 'Bueno', '/images/equipos/usco-eq-104.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-105', 'Analizador de Espectro', 'Rigol', 'DSA815', 'DSA8A242300505', 'Osciloscopios', 'Electrónica',
   'Bloque de Ingeniería · Laboratorio de Electrónica · Estante de instrumentos',
   'Analizador de espectro para caracterización de señales de radiofrecuencia.',
   '9 kHz – 1.5 GHz|RBW 100 Hz – 1 MHz|Generador de seguimiento opcional',
   'Cable de poder|Cable BNC-SMA|Cable USB', 'Regular', '/images/equipos/usco-eq-105.jpg', 'MANTENIMIENTO', TRUE, NULL, NULL, 1),
  ('USCO-EQ-200', 'Viscosímetro Rotacional Fann Modelo 35', 'Fann', '35SA', 'F35-8841', 'Análisis de fluidos', 'Petróleos',
   'Bloque de Ingeniería · Laboratorio de Petróleos · Mesa de fluidos 1',
   'Determinación de viscosidad plástica, punto cedente y geles de fluidos de perforación.',
   '6 velocidades (3–600 rpm)|Lectura directa de dial|Taza térmica incluida',
   'Taza de 350 mL|Juego de bob y rotor R1-B1|Cable de poder', 'Bueno', '/images/equipos/usco-eq-200.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-201', 'Balanza de Lodos Presurizada', 'Fann', 'Mud Balance 140', 'FMB-2217', 'Análisis de fluidos', 'Petróleos',
   'Bloque de Ingeniería · Laboratorio de Petróleos · Mesa de fluidos 1',
   'Medición de la densidad real del lodo presurizado, eliminando el aire incorporado.',
   'Rango 6–24 lb/gal|Precisión ±0.1 lb/gal|Copa presurizada',
   'Estuche|Émbolo de presurización|Contrapeso', 'Bueno', '/images/equipos/usco-eq-201.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-202', 'Filtro Prensa API de Baja Presión', 'Fann', 'Serie 300', 'FFP-0932', 'Análisis de fluidos', 'Petróleos',
   'Bloque de Ingeniería · Laboratorio de Petróleos · Mesa de fluidos 2',
   'Medición de pérdida de filtrado y espesor del revoque de fluidos de perforación a 100 psi.',
   'Presión 100 psi|Área de filtración 45 cm²|Celda en acero inoxidable',
   'Celda de lodo|Papel filtro Whatman (caja)|Cilindro graduado 25 mL|Regulador y manguera', 'Regular', '/images/equipos/usco-eq-202.jpg', 'MANTENIMIENTO', TRUE, NULL, NULL, 1),
  ('USCO-EQ-203', 'Hidrómetro API / Densímetro', 'Cole-Parmer', 'ASTM 3H', 'H3H-5520', 'Análisis de fluidos', 'Petróleos',
   'Bloque de Ingeniería · Laboratorio de Petróleos · Armario de vidriería',
   'Determinación de la gravedad API y la densidad de crudos y derivados del petróleo.',
   'Escala 29–41 °API|Termómetro incorporado|Norma ASTM D287',
   'Probeta de vidrio 500 mL|Estuche rígido|Certificado de calibración', 'Bueno', '/images/equipos/usco-eq-203.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-300', 'Estación Total', 'Topcon', 'GM-55', 'GM55-7B2210', 'Topografía', 'Civil',
   'Bloque de Ingeniería · Laboratorio de Civil · Gabinete de topografía',
   'Estación total electrónica para levantamientos y replanteo topográfico.',
   'Precisión angular 5"|Alcance 500 m sin prisma|Bluetooth y memoria interna',
   'Trípode de aluminio|Prisma con bastón|2 baterías y cargador|Estuche rígido', 'Excelente', '/images/equipos/usco-eq-300.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-301', 'Nivel Automático Óptico', 'Bosch', 'GOL 26 D', 'GOL26D-3318', 'Topografía', 'Civil',
   'Bloque de Ingeniería · Laboratorio de Civil · Gabinete de topografía',
   'Nivel óptico automático de 26 aumentos para nivelación de precisión.',
   '26 aumentos|Precisión 1.6 mm / 30 m|Compensador de péndulo',
   'Trípode|Mira de aluminio 5 m|Estuche|Plomada', 'Bueno', '/images/equipos/usco-eq-301.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-302', 'Esclerómetro Digital de Concreto (Martillo Schmidt)', 'Controls', 'Digi-Schmidt 2000', 'DS2-1129', 'Ensayos de materiales', 'Civil',
   'Bloque de Ingeniería · Laboratorio de Civil · Laboratorio de concretos',
   'Estimación no destructiva de la resistencia a compresión del concreto.',
   'Energía de impacto 2.207 J|Lectura digital|Memoria de ensayos',
   'Yunque de calibración|Piedra de pulido|Estuche', 'Bueno', '/images/equipos/usco-eq-302.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-303', 'Tamizadora Eléctrica de Agregados', 'Humboldt', 'H-4325', 'HT-4325-208', 'Ensayos de materiales', 'Civil',
   'Bloque de Ingeniería · Laboratorio de Civil · Laboratorio de agregados',
   'Agitador de tamices para análisis granulométrico de agregados finos y gruesos.',
   'Capacidad hasta 8 tamices de 8 pulg|Temporizador digital|Vibración horizontal y vertical',
   'Cable de poder|Tapa y fondo|Cepillo de limpieza', 'Bueno', '/images/equipos/usco-eq-303.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-400', 'Medidor Multiparámetro de Suelos (pH/Conductividad)', 'Hanna', 'HI 8314', 'H8314-6021', 'Medición de suelos', 'Agrícola',
   'Sede Neiva · Laboratorio de Agrícola · Mesa de suelos',
   'Medición de pH, conductividad y temperatura en muestras de suelo y soluciones nutritivas.',
   'pH 0–14|Conductividad 0–4000 µS/cm|Compensación de temperatura',
   'Electrodo de pH|Sonda de temperatura|Soluciones buffer 4 y 7|Estuche', 'Bueno', '/images/equipos/usco-eq-400.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-401', 'Estación Meteorológica Portátil', 'Davis', 'Vantage Vue', 'VV-6250-2210', 'Climatología', 'Agrícola',
   'Sede Neiva · Laboratorio de Agrícola · Bodega de campo',
   'Estación inalámbrica para temperatura, humedad, viento y precipitación.',
   'Alcance inalámbrico 300 m|Consola con registro|Pluviómetro de cazoleta',
   'Consola|Sensor integrado (ISS)|Soporte y mástil|Baterías', 'Excelente', '/images/equipos/usco-eq-401.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-402', 'Tensiómetro de Humedad de Suelo', 'Irrometer', 'Modelo SR', 'IRR-SR-3305', 'Medición de suelos', 'Agrícola',
   'Sede Neiva · Laboratorio de Agrícola · Bodega de campo',
   'Medición de la tensión de humedad del suelo para programar el riego.',
   'Rango 0–100 centibares|Manómetro de vacío|Cápsula de cerámica',
   'Tubo de 60 cm|Bomba de vacío manual|Agua desaireada', 'Bueno', '/images/equipos/usco-eq-402.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-403', 'GPS Submétrico', 'Garmin', 'GPSMAP 64st', 'GM64-5521', 'Topografía', 'Agrícola',
   'Sede Neiva · Laboratorio de Agrícola · Bodega de campo',
   'Receptor GNSS portátil para levantamiento de lotes y puntos de muestreo.',
   'GPS + GLONASS|Precisión submétrica con corrección|Pantalla de 2.6 pulg',
   'Cable USB|Baterías AA|Correa|Tarjeta microSD', 'Bueno', '/images/equipos/usco-eq-403.jpg', 'DISPONIBLE', TRUE, NULL, NULL, 1),
  ('USCO-EQ-299', 'Viscosímetro Analógico (obsoleto)', 'Fann', '35A', 'F35A-0099', 'Análisis de fluidos', 'Petróleos',
   'Bloque de Ingeniería · Laboratorio de Petróleos · Bodega',
   'Equipo retirado del inventario por obsolescencia.',
   'Lectura analógica',
   NULL, 'Deteriorado', '/images/equipos/usco-eq-299.jpg', 'DISPONIBLE', FALSE, 'Obsolescencia técnica; reemplazado por USCO-EQ-200', NOW(6) - INTERVAL 40 DAY, 1);
