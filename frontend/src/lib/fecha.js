// Utilidades de fecha. Formato uniforme en toda la app (PA-09): "20 oct 2026 · 17:45".
// El backend entrega ISO local sin zona (2026-10-20T17:45:00); new Date() lo interpreta como hora local.

const pad = (n) => String(n).padStart(2, '0')
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado']

export const toIso = (d) =>
  `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`

export const toDia = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`

export const parse = (s) => (s ? new Date(s) : null)

export const fmtHora = (s) => {
  const d = parse(s)
  return d ? `${pad(d.getHours())}:${pad(d.getMinutes())}` : '—'
}

export const fmtFecha = (s) => {
  const d = parse(s)
  return d ? `${pad(d.getDate())} ${MESES[d.getMonth()]} ${d.getFullYear()}` : '—'
}

export const fmtFechaHora = (s) => (s ? `${fmtFecha(s)} · ${fmtHora(s)}` : '—')

/** "martes 23 sep · 14:00–16:00" */
export const fmtFranja = (ini, fin) => {
  const d = parse(ini)
  if (!d) return '—'
  return `${DIAS[d.getDay()]} ${d.getDate()} ${MESES[d.getMonth()]} · ${fmtHora(ini)}–${fmtHora(fin)}`
}

export const fmtDiaCorto = (d) => `${DIAS[d.getDay()].slice(0, 3)} ${d.getDate()} ${MESES[d.getMonth()]}`

export const fmtDuracion = (min) => {
  const m = Math.max(0, Math.round(min))
  if (m < 60) return `${m} min`
  const h = Math.floor(m / 60)
  const r = m % 60
  if (h >= 24) {
    const d = Math.floor(h / 24)
    return `${d} d ${h % 24} h`
  }
  return r ? `${h} h ${pad(r)} min` : `${h} h`
}

/** Texto de vencimiento: "vence en 1 h 05 min" o "venció hace 1 h 35 min". */
export const fmtVencimiento = (limiteIso, ahora = new Date()) => {
  const min = (parse(limiteIso) - ahora) / 60000
  return min >= 0 ? `vence en ${fmtDuracion(min)}` : `venció hace ${fmtDuracion(-min)}`
}

export const minutosEntre = (a, b) => (parse(b) - parse(a)) / 60000
