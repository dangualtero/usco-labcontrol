"""
Verificación del MVP contra la API real (Spring Boot + MySQL). Reproducible:

    1) iniciar-mysql.ps1  y  la API (puerto 8080) con la base recién cargada (datos iniciales)
    2) python pruebas/verificacion.py [URL_API]        (por defecto http://127.0.0.1:8080/api)

Ejecuta 4 bloques y escribe pruebas/resultados.json:
  E2E  recorrido funcional de extremo a extremo
  SEG  matriz de control de acceso por rol (RNF-03)
  PC   concurrencia: N solicitudes simultáneas sobre un mismo equipo (RNF-02)
  PR   latencia de endpoints principales (RNF-04)
Usa solo la librería estándar de Python. Las credenciales son las de la carga inicial (README.md).
"""
import datetime, json, os, statistics, sys, threading, time, urllib.error, urllib.request
sys.stdout.reconfigure(encoding="utf-8")

BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:8080/api'
AQUI = os.path.dirname(os.path.abspath(__file__))
CRED = {
    'ESTUDIANTE': ('estudiante@usco.edu.co', 'Est#USCO2026'),
    'DOCENTE': ('docente@usco.edu.co', 'Doc#USCO2026'),
    'AUXILIAR': ('auxiliar@usco.edu.co', 'Aux#USCO2026'),
    'COORDINADOR': ('coordinador@usco.edu.co', 'Coord#USCO2026'),
}


def llamar(metodo, ruta, cuerpo=None, token=None):
    req = urllib.request.Request(BASE + ruta, method=metodo, data=None if cuerpo is None else json.dumps(cuerpo).encode(),
                                 headers={'Content-Type': 'application/json', **({'Authorization': 'Bearer ' + token} if token else {})})
    try:
        with urllib.request.urlopen(req) as r:
            t = r.read().decode()
            return r.status, (json.loads(t) if t and 'json' in r.headers.get('content-type', '') else t)
    except urllib.error.HTTPError as e:
        t = e.read().decode()
        try: return e.code, json.loads(t)
        except Exception: return e.code, t


def entrar(correo, clave):
    s, d = llamar('POST', '/auth/login', {'email': correo, 'password': clave})
    return d['token'] if s == 200 else None


TOK = {rol: entrar(*c) for rol, c in CRED.items()}
TOK['DANIEL'] = entrar('daniel.gualtero@usco.edu.co', 'Daniel#USCO2026')
RES = {'fecha': datetime.datetime.now().isoformat(timespec='seconds'), 'api': BASE}

# ------------------------------------------------------------------ E2E
casos = []
def verificar(id_, descripcion, ok, detalle=''):
    casos.append({'id': id_, 'descripcion': descripcion, 'ok': bool(ok), 'detalle': str(detalle)[:120]})
    print(('PASS ' if ok else 'FAIL ') + id_, descripcion, detalle if not ok else '')

E, A, C = TOK['ESTUDIANTE'], TOK['AUXILIAR'], TOK['COORDINADOR']
n = 0
def v(desc, ok, det=''):
    global n; n += 1; verificar(f'E2E-{n:02d}', desc, ok, det)

for rol, (c, p) in CRED.items():
    s, d = llamar('POST', '/auth/login', {'email': c, 'password': p})
    v(f'login {c} identifica el rol {rol}', s == 200 and d['usuario']['rol'] == rol)
s, d = llamar('POST', '/auth/login', {'email': 'cesar.pimiento@usco.edu.co', 'password': 'Cesar#USCO2026', 'recordar': True}); v('login de un autor (ESTUDIANTE) con «recordar sesión»', s == 200 and d['expiraEnSegundos'] > 86400)
s, d = llamar('POST', '/auth/login', {'email': 'estudiante@usco.edu.co', 'password': 'incorrecta'}); v('credenciales inválidas → mensaje genérico (PS-08)', s == 401 and d['error'] == 'Credenciales inválidas')
s, d = llamar('POST', '/auth/login', {'email': '', 'password': 'x'}); v('correo vacío rechazado (PV-01)', s == 400)
s, d = llamar('POST', '/auth/login', {'email': 'usuario123', 'password': 'x'}); v('formato de correo inválido (PV-02)', s == 400 and '@usco.edu.co' in d['error'])
s, eq = llamar('GET', '/equipos', token=E); v('catálogo con 18 equipos y fotografía', s == 200 and len(eq) == 18 and all(x['fotoUrl'] for x in eq))
s, det = llamar('GET', '/equipos/USCO-EQ-103', token=E); v('ficha técnica completa (serie, ubicación, accesorios)', det['equipo']['numeroSerie'] and det['equipo']['ubicacion'] and len(det['equipo']['accesorios']) >= 2)
v('el catálogo no expone quién tiene un equipo prestado (PS-05)', 'solicitante' not in json.dumps(llamar('GET', '/equipos/USCO-EQ-102', token=E)[1]).lower())
ini = (datetime.datetime.now() + datetime.timedelta(days=1)).replace(hour=14, minute=0, second=0, microsecond=0)
fin = ini + datetime.timedelta(hours=2)
f = '%Y-%m-%dT%H:%M:%S'
s, r = llamar('POST', '/prestamos/reservas', {'placa': 'USCO-EQ-104', 'asignatura': 'Electrónica de Potencia', 'docente': 'Ing. Ortiz', 'inicio': ini.strftime(f), 'fin': fin.strftime(f)}, E)
v('estudiante solicita un préstamo', s == 200 and r['codigo'].startswith('RES-') and r['docente'] == 'Ing. Ortiz'); rid = r.get('id')
s, _ = llamar('POST', '/prestamos/reservas', {'placa': 'USCO-EQ-104', 'asignatura': 'X', 'inicio': None, 'fin': None}, E); v('solicitud sin franja horaria rechazada (PV-03)', s == 400)
s, _ = llamar('POST', '/prestamos/reservas', {'placa': 'USCO-EQ-102', 'asignatura': 'X', 'inicio': ini.strftime(f), 'fin': fin.strftime(f)}, E); v('equipo prestado no se puede solicitar (PV-05)', s == 409)
s, p = llamar('GET', '/ventanilla/panel', token=A); v('auxiliar ve la solicitud pendiente', any(x['id'] == rid for x in p['reservas']))
s, _ = llamar('POST', f'/ventanilla/reservas/{rid}/rechazar', {'motivo': 'no'}, A); v('rechazo exige un motivo', s == 400)
s, r2 = llamar('POST', f'/ventanilla/reservas/{rid}/rechazar', {'motivo': 'Equipo reservado para práctica de laboratorio'}, A); v('auxiliar rechaza la solicitud con motivo', s == 200 and r2['estado'] == 'RECHAZADO')
s, mis = llamar('GET', '/prestamos/mis', token=E); v('estudiante ve el rechazo en su historial', any(x['id'] == rid and x['estado'] == 'RECHAZADO' for x in mis))
s, r = llamar('POST', '/prestamos/reservas', {'placa': 'USCO-EQ-104', 'asignatura': 'Electrónica', 'inicio': ini.strftime(f), 'fin': fin.strftime(f)}, E)
s, c = llamar('DELETE', f'/prestamos/reservas/{r["id"]}', token=E); v('estudiante cancela una reserva pendiente (PV-06)', s == 200 and c['estado'] == 'CANCELADO')
s, x = llamar('POST', '/ventanilla/salida', {'placa': 'EQ-99999', 'documento': '20211100456'}, A); v('placa con formato inválido (PU-07)', s == 400 and 'USCO-EQ-000' in x['error'])
s, x = llamar('POST', '/ventanilla/salida', {'placa': 'USCO-EQ-999', 'documento': '20211100456'}, A); v('placa inexistente (PV-04)', s == 404 and x['error'] == 'El equipo no existe en el inventario activo')
s, x = llamar('POST', '/ventanilla/salida', {'placa': 'USCO-EQ-104', 'documento': '20201100011'}, A); v('límite de 3 equipos por usuario (PV-10)', s == 409 and x['error'] == 'Límite máximo de equipos alcanzado')
s, sal = llamar('POST', '/ventanilla/salida', {'placa': 'USCO-EQ-104', 'documento': '20211100456', 'horas': 2}, A); v('auxiliar registra la salida (búsqueda por código estudiantil)', s == 200 and sal['estado'] == 'ACTIVO')
s, e1 = llamar('GET', '/equipos/USCO-EQ-104', token=E); v('el equipo pasa a «Prestado» en el catálogo', e1['equipo']['estado'] == 'PRESTADO')
s, x = llamar('POST', '/ventanilla/entrada', {'prestamoId': sal['id'], 'danos': True, 'observaciones': ' '}, A); v('daños sin observación rechazados', s == 400)
s, en = llamar('POST', '/ventanilla/entrada', {'prestamoId': sal['id'], 'danos': True, 'observaciones': 'Punta quemada'}, A); v('entrada con daños: mantenimiento + incidencia (PF-10)', s == 200 and en['equipo']['estado'] == 'MANTENIMIENTO' and en['incidencia'].startswith('INC-'))
s, ea = llamar('POST', '/ventanilla/equipos/USCO-EQ-104/estado', {'estado': 'DISPONIBLE'}, A); v('auxiliar reactiva el equipo', s == 200 and ea['estado'] == 'DISPONIBLE')
s, k = llamar('GET', '/coordinador/kpis', token=C); v('coordinador consulta KPIs desde MySQL', s == 200 and k['totalEquipos'] >= 18)
s, h = llamar('GET', '/coordinador/historial', token=C); v('el historial registra los eventos (incidencia incluida)', any(x['tipo'] == 'INCIDENCIA' for x in h[:15]))
s, p2 = llamar('GET', '/ventanilla/panel', token=A); v('alertas: el panel identifica los 2 préstamos vencidos (RF-04)', p2['resumen']['vencidos'] == 2 and sum(1 for x in p2['activos'] if x['vencido']) == 2)
s, mis2 = llamar('GET', '/prestamos/mis', token=E); v('historial personal con préstamos devueltos y observaciones (RF-06)', sum(1 for x in mis2 if x['estado'] == 'DEVUELTO' and x['observaciones']) >= 4)
s, ct = llamar('GET', '/ventanilla/cierre-turno', token=A); pend = len(ct['pendientes']); v('cierre de turno: lista los equipos pendientes (RF-09)', s == 200 and pend >= 1)
s, tr = llamar('POST', '/ventanilla/cierre-turno', {'auxiliarDestinoId': ct['auxiliares'][0]['id']}, A); v('cierre de turno: transfiere la custodia al auxiliar entrante (RF-09)', s == 200 and tr['transferidos'] == pend)
s, ct2 = llamar('GET', '/ventanilla/cierre-turno', token=A); v('tras transferir no quedan equipos bajo la custodia del auxiliar saliente', len(ct2['pendientes']) == 0)
s, csv = llamar('GET', '/coordinador/reporte.csv', token=C); v('reporte exportado con campos personales enmascarados (PS-10)', '••••••' in csv and '1075123456' not in csv)
nuevo = {'placa': 'USCO-EQ-420', 'nombre': 'Medidor de Clorofila', 'marca': 'Konica Minolta', 'modelo': 'SPAD-502', 'numeroSerie': 'SPAD-9001', 'categoria': 'Análisis de cultivos',
         'laboratorio': 'Agrícola', 'ubicacion': 'Sede Neiva · Bodega', 'descripcion': 'Clorofila foliar', 'specs': ['0-99 SPAD'], 'accesorios': ['Estuche', 'Baterías'], 'estadoFisico': 'Excelente', 'fotoUrl': '/images/equipos/usco-eq-420.jpg', 'estado': 'DISPONIBLE'}
s, cr = llamar('POST', '/coordinador/equipos', nuevo, C); v('coordinador crea un equipo', s == 200)
s, _ = llamar('POST', '/coordinador/equipos', nuevo, C); v('placa duplicada rechazada', s == 409)
s, ed = llamar('PUT', '/coordinador/equipos/USCO-EQ-420', {**nuevo, 'nombre': 'Medidor de Clorofila SPAD'}, C); v('coordinador edita el equipo', s == 200 and ed['equipo']['nombre'].endswith('SPAD'))
s, m = llamar('POST', '/coordinador/equipos/USCO-EQ-420/estado', {'estado': 'MANTENIMIENTO', 'motivo': 'Calibración anual'}, C); v('coordinador envía el equipo a mantenimiento', s == 200 and m['estado'] == 'MANTENIMIENTO')
s, _ = llamar('POST', '/coordinador/equipos/USCO-EQ-300/baja', {'motivo': 'Prueba de baja de equipo prestado'}, C); v('no se puede dar de baja un equipo prestado', s == 409)
s, b = llamar('POST', '/coordinador/equipos/USCO-EQ-420/baja', {'motivo': 'Prueba de baja lógica del sistema'}, C); v('baja lógica (soft delete) del equipo (PS-08)', s == 200 and b['activo'] is False)
s, eq = llamar('GET', '/equipos', token=E); v('el equipo dado de baja sale del catálogo', all(x['placa'] != 'USCO-EQ-420' for x in eq))
s, adm = llamar('GET', '/coordinador/equipos', token=C); v('la baja se conserva para auditoría', any(x['equipo']['placa'] == 'USCO-EQ-420' and not x['activo'] for x in adm))
s, u = llamar('POST', '/coordinador/usuarios', {'nombre': 'Prueba Temporal', 'correo': 'prueba.temporal@usco.edu.co', 'rol': 'DOCENTE', 'documento': '1099777001', 'password': 'Temporal#2026'}, C); v('coordinador crea un usuario con rol', s == 200)
t = entrar('prueba.temporal@usco.edu.co', 'Temporal#2026'); v('el usuario nuevo inicia sesión con su rol', t is not None)
llamar('PUT', f'/coordinador/usuarios/{u["id"]}/activo', {'activo': False}, C)
v('el token de un usuario desactivado deja de funcionar', llamar('GET', '/equipos', token=t)[0] == 401)
v('un usuario desactivado no puede iniciar sesión', entrar('prueba.temporal@usco.edu.co', 'Temporal#2026') is None)
s, _ = llamar('POST', '/auth/cambiar-clave', {'actual': 'mala', 'nueva': 'Nueva#Clave2026'}, TOK['DOCENTE']); v('cambio de contraseña exige la contraseña actual', s == 400)
RES['e2e'] = {'total': len(casos), 'aprobados': sum(c['ok'] for c in casos), 'casos': casos}

# ------------------------------------------------------------------ SEG: matriz de control de acceso
ENDPOINTS = [  # (método, ruta, cuerpo, roles autorizados)
    ('GET', '/equipos', None, {'ESTUDIANTE', 'DOCENTE', 'AUXILIAR', 'COORDINADOR'}),
    ('GET', '/auth/yo', None, {'ESTUDIANTE', 'DOCENTE', 'AUXILIAR', 'COORDINADOR'}),
    ('POST', '/auth/cambiar-clave', {'actual': 'x', 'nueva': 'x'}, {'ESTUDIANTE', 'DOCENTE', 'AUXILIAR', 'COORDINADOR'}),
    ('GET', '/prestamos/mis', None, {'ESTUDIANTE', 'DOCENTE'}),
    ('POST', '/prestamos/reservas', {}, {'ESTUDIANTE', 'DOCENTE'}),
    ('DELETE', '/prestamos/reservas/999999', None, {'ESTUDIANTE', 'DOCENTE'}),
    ('GET', '/ventanilla/panel', None, {'AUXILIAR'}),
    ('GET', '/ventanilla/cierre-turno', None, {'AUXILIAR'}),
    ('POST', '/ventanilla/salida', {}, {'AUXILIAR'}),
    ('POST', '/ventanilla/entrada', {}, {'AUXILIAR'}),
    ('POST', '/ventanilla/reservas/999999/rechazar', {}, {'AUXILIAR'}),
    ('POST', '/ventanilla/equipos/USCO-EQ-999/estado', {}, {'AUXILIAR'}),
    ('GET', '/coordinador/kpis', None, {'COORDINADOR'}),
    ('GET', '/coordinador/historial', None, {'COORDINADOR'}),
    ('GET', '/coordinador/reporte.csv', None, {'COORDINADOR'}),
    ('GET', '/coordinador/equipos', None, {'COORDINADOR'}),
    ('POST', '/coordinador/equipos', {}, {'COORDINADOR'}),
    ('PUT', '/coordinador/equipos/USCO-EQ-999', {}, {'COORDINADOR'}),
    ('POST', '/coordinador/equipos/USCO-EQ-999/baja', {}, {'COORDINADOR'}),
    ('GET', '/coordinador/usuarios', None, {'COORDINADOR'}),
    ('POST', '/coordinador/usuarios', {}, {'COORDINADOR'}),
    ('POST', '/coordinador/usuarios/999999/clave', {}, {'COORDINADOR'}),
]
matriz, violaciones, total = [], [], 0
for metodo, ruta, cuerpo, autorizados in ENDPOINTS:
    fila = {'endpoint': f'{metodo} {ruta}'}
    for rol in ['ESTUDIANTE', 'DOCENTE', 'AUXILIAR', 'COORDINADOR', 'SIN SESIÓN']:
        s, _ = llamar(metodo, ruta, cuerpo, TOK.get(rol))
        permitido = s not in (401, 403)
        esperado = rol in autorizados
        fila[rol] = s
        total += 1
        if permitido != esperado: violaciones.append(f'{metodo} {ruta} como {rol}: HTTP {s}')
    matriz.append(fila)
RES['seg'] = {'peticiones': total, 'violaciones': violaciones, 'matriz': matriz}
print(f'SEG: {total} peticiones, {len(violaciones)} violaciones de acceso')

# ------------------------------------------------------------------ PC: concurrencia
RONDAS = ['USCO-EQ-101', 'USCO-EQ-200', 'USCO-EQ-203', 'USCO-EQ-301', 'USCO-EQ-302', 'USCO-EQ-400', 'USCO-EQ-403', 'USCO-EQ-104']
DOCS = ['20211100456', '20211100012', '20221100013', '20211100014', '20221100003', '36600200']
SIMULTANEAS = 12
filas = []
for placa in RONDAS:
    resultados = []
    barrera = threading.Barrier(SIMULTANEAS)
    def hilo(i):
        barrera.wait()
        s, d = llamar('POST', '/ventanilla/salida', {'placa': placa, 'documento': DOCS[i % len(DOCS)], 'horas': 1}, A)
        resultados.append((s, d))
    hs = [threading.Thread(target=hilo, args=(i,)) for i in range(SIMULTANEAS)]
    [h.start() for h in hs]; [h.join() for h in hs]
    ok = [d for s, d in resultados if s == 200]
    rech = [s for s, _ in resultados if s != 200]
    s, panel = llamar('GET', '/ventanilla/panel', token=A)
    activos = sum(1 for x in panel['activos'] if x['placa'] == placa)
    filas.append({'placa': placa, 'solicitudes': SIMULTANEAS, 'exitosas': len(ok), 'rechazadas': len(rech), 'codigos_rechazo': sorted(set(rech)), 'prestamos_activos_en_bd': activos})
    for d in ok:
        llamar('POST', '/ventanilla/entrada', {'prestamoId': d['id'], 'buenEstado': True}, A)
dobles = sum(1 for f in filas if f['prestamos_activos_en_bd'] > 1 or f['exitosas'] > 1)
RES['pc'] = {'rondas': len(filas), 'simultaneas_por_ronda': SIMULTANEAS, 'solicitudes_totales': len(filas) * SIMULTANEAS,
             'exitosas_totales': sum(f['exitosas'] for f in filas), 'dobles_asignaciones': dobles, 'detalle': filas}
print(f'PC: {RES["pc"]["solicitudes_totales"]} solicitudes, {RES["pc"]["exitosas_totales"]} exitosas, {dobles} dobles asignaciones')

# ------------------------------------------------------------------ PR: latencia
def medir(nombre, metodo, ruta, cuerpo=None, token=None, repeticiones=120):
    for _ in range(10): llamar(metodo, ruta, cuerpo, token)          # calentamiento
    ms = []
    for _ in range(repeticiones):
        t0 = time.perf_counter(); llamar(metodo, ruta, cuerpo, token); ms.append((time.perf_counter() - t0) * 1000)
    ms.sort()
    return {'operacion': nombre, 'peticion': f'{metodo} {ruta}', 'n': repeticiones, 'p50_ms': round(statistics.median(ms), 1),
            'p95_ms': round(ms[int(0.95 * len(ms)) - 1], 1), 'max_ms': round(ms[-1], 1)}
RES['pr'] = [
    medir('Inicio de sesión (BCrypt)', 'POST', '/auth/login', {'email': 'estudiante@usco.edu.co', 'password': 'Est#USCO2026'}),
    medir('Consulta de disponibilidad (catálogo)', 'GET', '/equipos', token=E),
    medir('Ficha técnica de un equipo', 'GET', '/equipos/USCO-EQ-103', token=E),
    medir('Historial personal del usuario', 'GET', '/prestamos/mis', token=E),
    medir('Panel de ventanilla', 'GET', '/ventanilla/panel', token=A),
    medir('KPIs del coordinador', 'GET', '/coordinador/kpis', token=C),
    medir('Historial de movimientos', 'GET', '/coordinador/historial', token=C),
]
for r in RES['pr']: print(f"PR: {r['operacion']}: p50={r['p50_ms']} ms p95={r['p95_ms']} ms")

with open(os.path.join(AQUI, 'resultados.json'), 'w', encoding='utf-8') as fh:
    json.dump(RES, fh, ensure_ascii=False, indent=1)
fallos = [c for c in casos if not c['ok']]
print(f"\nE2E: {RES['e2e']['aprobados']}/{RES['e2e']['total']} · SEG violaciones: {len(violaciones)} · PC dobles asignaciones: {dobles}")
sys.exit(1 if (fallos or violaciones or dobles) else 0)
