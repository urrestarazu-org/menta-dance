/** @type {import('tailwindcss').Config} */
const rgb = (token) => `rgb(var(--color-${token}) / <alpha-value>)`;

module.exports = {
  // Content scan: ONLY Thymeleaf templates and BFF source scripts.
  // Never add generated output or node_modules here.
  content: [
    './src/main/resources/templates/**/*.html',
    './src/main/frontend/js/**/*.js',
  ],
  theme: {
    extend: {
      colors: {
        // Menta palette as semantic tokens. The raw values live once, as CSS
        // variables, in src/main/frontend/css/input.css.
        brand: {
          deep: rgb('brand-deep'),
          action: rgb('brand-action'),
          bright: rgb('brand-bright'),
          light: rgb('brand-light'),
          surface: rgb('brand-surface'),
        },
      },
      fontFamily: {
        sans: [
          'ui-sans-serif',
          'system-ui',
          '-apple-system',
          'Segoe UI',
          'Roboto',
          'Helvetica Neue',
          'Arial',
          'sans-serif',
        ],
      },
    },
  },
  plugins: [require('@tailwindcss/forms'), require('@tailwindcss/typography')],
};
