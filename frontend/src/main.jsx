import React from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import { SedesProvider } from './context/SedeContext.jsx'
import { ToastProvider } from './context/ToastContext.jsx'
import { TemaProvider } from './context/TemaContext.jsx'
// Fuentes servidas desde el propio sitio (sin Google Fonts): permite una CSP estricta y no depende de terceros.
import '@fontsource/libre-franklin/latin-500.css'
import '@fontsource/libre-franklin/latin-600.css'
import '@fontsource/libre-franklin/latin-700.css'
import '@fontsource/libre-franklin/latin-800.css'
import '@fontsource/source-sans-3/latin-400.css'
import '@fontsource/source-sans-3/latin-500.css'
import '@fontsource/source-sans-3/latin-600.css'
import '@fontsource/source-sans-3/latin-700.css'
import '@fontsource/jetbrains-mono/latin-500.css'
import '@fontsource/jetbrains-mono/latin-600.css'
import './index.css'

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <TemaProvider>
        <ToastProvider>
          <AuthProvider>
            <SedesProvider>
              <App />
            </SedesProvider>
          </AuthProvider>
        </ToastProvider>
      </TemaProvider>
    </BrowserRouter>
  </React.StrictMode>,
)
