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
          900: '#1a2332',
          800: '#243044',
          700: '#2d3d56',
          600: '#3a5070',
          500: '#4a6589',
          400: '#6b89ad',
          300: '#8fabc8',
          200: '#b4cde0',
          100: '#daeaf5',
          50: '#f0f7fc',
        },
      },
    },
  },
  plugins: [],
}
