/** Paleta oficial USCO (medida del portal www.usco.edu.co) + colores semánticos exigidos por el QA. */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        usco: {
          DEFAULT: '#8F141B', // rojo vino tinto institucional
          dark: '#731016',
          darker: '#5C0D12',
          soft: 'rgb(var(--c-usco-soft) / <alpha-value>)',
        },
        oro: { DEFAULT: '#C5A059', claro: '#DFD4A6', oliva: '#423C16' },
        pizarra: { DEFAULT: 'rgb(var(--c-pizarra) / <alpha-value>)', oscuro: 'rgb(var(--c-pizarra-oscuro) / <alpha-value>)' },
        fondo: 'rgb(var(--c-fondo) / <alpha-value>)',
        superficie: 'rgb(var(--c-superficie) / <alpha-value>)',
        // PA-07 / PF-08 / PF-09: acciones críticas diferenciadas por color funcional
        salida: { DEFAULT: '#1E3A8A', dark: '#172E6E' },     // Registrar Salida (préstamo)
        entrada: { DEFAULT: '#059669', dark: '#047857' },    // Registrar Entrada (devolución)
        alerta: { fondo: 'rgb(var(--c-alerta-fondo) / <alpha-value>)', borde: 'rgb(var(--c-alerta-borde) / <alpha-value>)', texto: 'rgb(var(--c-alerta-texto) / <alpha-value>)' },
      },
      fontFamily: {
        sans: ['"Source Sans 3"', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'],
        titulo: ['"Libre Franklin"', '"Source Sans 3"', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      boxShadow: {
        tarjeta: '0 1px 2px rgba(15,23,42,.06), 0 4px 14px rgba(15,23,42,.05)',
      },
    },
  },
  plugins: [],
}
