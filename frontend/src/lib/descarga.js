/** Descarga un texto como archivo (CSV UTF-8 con BOM para que Excel respete tildes). */
export function descargarTexto(nombre, contenido, tipo = 'text/csv;charset=utf-8') {
  const blob = new Blob([contenido], { type: tipo })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = nombre
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Descarga un archivo privado (documento, cotización, evidencia…) con la sesión activa. */
export async function descargarArchivo(archivo) {
  const { api } = await import('../api/index.js')
  const blob = await api.archivo(archivo.id)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = archivo.nombre || 'archivo'
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Abre una imagen o PDF privado en una pestaña nueva (sin exponer el token en la URL). */
export async function verArchivo(archivo) {
  const ventana = window.open('', '_blank')
  const { api } = await import('../api/index.js')
  try {
    const blob = await api.archivo(archivo.id)
    const url = URL.createObjectURL(new Blob([blob], { type: archivo.tipoContenido || blob.type }))
    if (ventana) ventana.location.href = url
    else window.location.href = url
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (e) {
    ventana?.close()
    throw e
  }
}

/** ¿Se puede ver en el navegador (imagen o PDF)? */
export const visible = (archivo) => /^(image\/|application\/pdf)/.test(archivo?.tipoContenido ?? '')
