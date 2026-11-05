/**
 * Prepara una fotografía elegida desde el computador o el celular antes de subirla:
 * la reduce a máximo 1600 px por lado y la convierte a JPEG (calidad 0,85). Así una foto de 8–12 MB tomada con el
 * celular queda en unos cientos de KB y sube rápido incluso con datos móviles.
 * Si el navegador no puede procesarla (p. ej. HEIC en un PC), se envía el archivo original y el servidor decide.
 */
export const TIPOS_FOTO = ['image/jpeg', 'image/png', 'image/webp']
export const MAX_BYTES = 5 * 1024 * 1024
const LADO = 1600

export async function prepararFoto(archivo) {
  if (!archivo) throw new Error('Selecciona una fotografía')
  if (archivo.type && !archivo.type.startsWith('image/')) throw new Error('El archivo no es una imagen')
  let bitmap
  try {
    bitmap = await cargar(archivo)
  } catch {
    if (!TIPOS_FOTO.includes(archivo.type)) throw new Error('Formato no compatible: usa una foto JPG, PNG o WEBP')
    if (archivo.size > MAX_BYTES) throw new Error('La fotografía supera los 5 MB permitidos')
    return archivo
  }
  const { width: w, height: h } = bitmap
  const escala = Math.min(1, LADO / Math.max(w, h))
  // Ya es pequeña y de un formato aceptado: se sube tal cual (conserva la transparencia de un PNG).
  if (escala === 1 && TIPOS_FOTO.includes(archivo.type) && archivo.size <= 1.5 * 1024 * 1024) return archivo
  const lienzo = document.createElement('canvas')
  lienzo.width = Math.round(w * escala)
  lienzo.height = Math.round(h * escala)
  const ctx = lienzo.getContext('2d')
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, lienzo.width, lienzo.height)
  ctx.drawImage(bitmap, 0, 0, lienzo.width, lienzo.height)
  bitmap.close?.()
  for (const calidad of [0.85, 0.75, 0.6]) {
    const blob = await new Promise((ok) => lienzo.toBlob(ok, 'image/jpeg', calidad))
    if (blob && blob.size <= MAX_BYTES) {
      const nombre = (archivo.name || 'foto').replace(/\.[^.]+$/, '') + '.jpg'
      return new File([blob], nombre, { type: 'image/jpeg' })
    }
  }
  throw new Error('La fotografía supera los 5 MB permitidos')
}

/** Decodifica respetando la orientación EXIF (fotos verticales del celular). */
async function cargar(archivo) {
  if ('createImageBitmap' in window) {
    try { return await createImageBitmap(archivo, { imageOrientation: 'from-image' }) } catch { /* sigue con <img> */ }
  }
  const url = URL.createObjectURL(archivo)
  try {
    const img = new Image()
    img.src = url
    await img.decode()
    return img
  } finally { URL.revokeObjectURL(url) }
}

export const tamanoLegible = (b) => (b >= 1024 * 1024 ? `${(b / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`)
