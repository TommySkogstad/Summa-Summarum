/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'summa': {
          950: '#0d1b2a',
          900: '#0d1b2a',
          800: '#1b2d42',
          700: '#2e4260',
          600: '#4a6080',
          500: '#7a96b0',
          400: '#b8cad8',
          300: '#e4eaf0',
          200: '#edf1f5',
          100: '#f4f5f2',
          50: '#f4f5f2',
        },
      },
      fontFamily: {
        sans: ['DM Sans', 'sans-serif'],
        mono: ['DM Mono', 'monospace'],
      },
    },
  },
  plugins: [],
}
