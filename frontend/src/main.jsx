import React from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import { AuthProvider } from './context/AuthContext.jsx'
import { SedesProvider } from './context/SedeContext.jsx'
import { ToastProvider } from './context/ToastContext.jsx'
import { TemaProvider } from './context/TemaContext.jsx'
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
