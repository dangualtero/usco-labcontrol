import { useEffect, useState } from 'react'
import { KeyRound } from 'lucide-react'
import { api } from '../api/index.js'
import { useAuth } from '../context/AuthContext.jsx'
import { useToast } from '../context/ToastContext.jsx'
import { GRUPOS } from '../lib/constantes.js'
import { Campo, ErrorCaja, Modal } from './ui.jsx'

/** Mi cuenta: datos del perfil (solo lectura) y cambio de contraseña contra la API. */
export default function CuentaModal({ abierto, onCerrar }) {
  const { usuario } = useAuth()
  const toast = useToast()
  const [actual, setActual] = useState('')
  const [nueva, setNueva] = useState('')
  const [repite, setRepite] = useState('')
  const [error, setError] = useState('')
  const [enviando, setEnviando] = useState(false)

  useEffect(() => { if (abierto) { setActual(''); setNueva(''); setRepite(''); setError('') } }, [abierto])

  const guardar = async (e) => {
    e.preventDefault()
    setError('')
    if (!actual) return setError('Ingresa tu contraseña actual')
    if (nueva.length < 8 || !/[A-Za-z]/.test(nueva) || !/\d/.test(nueva)) return setError('La nueva contraseña debe tener al menos 8 caracteres, con letras y números')
    if (nueva !== repite) return setError('La confirmación no coincide con la nueva contraseña')
    setEnviando(true)
    try { await api.cambiarClave(actual, nueva); toast.exito('Contraseña actualizada'); onCerrar() }
    catch (err) { setError(err.message) } finally { setEnviando(false) }
  }

  return (
    <Modal abierto={abierto} onCerrar={onCerrar} titulo="Mi cuenta" subtitulo={usuario?.correo}
      pie={<>
        <button type="button" className="btn-outline" onClick={onCerrar}>Cancelar</button>
        <button type="submit" form="form-clave" className="btn-primary" disabled={enviando}><KeyRound className="h-4 w-4" aria-hidden="true" /> {enviando ? 'Guardando…' : 'Cambiar contraseña'}</button>
      </>}>
      <dl className="mb-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 rounded-xl bg-slate-50 p-3 text-sm ring-1 ring-slate-200">
        <dt className="font-semibold text-pizarra">Nombre</dt><dd className="font-bold">{usuario?.nombre}</dd>
        <dt className="font-semibold text-pizarra">Rol</dt><dd className="font-bold">{GRUPOS[usuario?.grupo]?.etiqueta} ({usuario?.rol})</dd>
        {usuario?.programa && <><dt className="font-semibold text-pizarra">Programa</dt><dd className="font-bold">{usuario.programa}</dd></>}
        {usuario?.codigoEstudiantil && <><dt className="font-semibold text-pizarra">Código</dt><dd className="font-mono font-bold">{usuario.codigoEstudiantil}</dd></>}
      </dl>
      <form id="form-clave" onSubmit={guardar} className="space-y-3">
        <Campo etiqueta="Contraseña actual" htmlFor="c-actual"><input id="c-actual" type="password" autoComplete="current-password" className="input" value={actual} onChange={(e) => setActual(e.target.value)} /></Campo>
        <Campo etiqueta="Nueva contraseña" htmlFor="c-nueva" ayuda="Mínimo 8 caracteres, con letras y números."><input id="c-nueva" type="password" autoComplete="new-password" className="input" value={nueva} onChange={(e) => setNueva(e.target.value)} /></Campo>
        <Campo etiqueta="Confirmar nueva contraseña" htmlFor="c-repite"><input id="c-repite" type="password" autoComplete="new-password" className="input" value={repite} onChange={(e) => setRepite(e.target.value)} /></Campo>
        <ErrorCaja mensaje={error} />
      </form>
    </Modal>
  )
}
