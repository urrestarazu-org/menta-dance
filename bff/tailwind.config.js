/** @type {import('tailwindcss').Config} */
const rgb = (token) => `rgb(var(--color-${token}) / <alpha-value>)`;

module.exports = {
  // Content scan: ONLY Thymeleaf templates and BFF source scripts.
  // Never add generated output or node_modules here.
  content: [
    './src/main/resources/templates/**/*.html',
    './src/main/frontend/js/**/*.js',
  ],
  // Component library classes (src/main/frontend/css/input.css, @layer
  // components) ship as a whole: slice 3 migrates the remaining views and
  // Tailwind would otherwise drop every component no template uses yet.
  safelist: [
    'btn',
    'btn-primary',
    'btn-secondary',
    'field',
    'field-label',
    'field-input',
    'field-error',
    'card',
    'badge',
    'badge-success',
    'badge-warning',
    'badge-danger',
    'badge-neutral',
    'container-page',
    'alert',
    'alert-error',
    'alert-success',
    'alert-info',
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
        feedback: {
          error: rgb('feedback-error'),
          'error-surface': rgb('feedback-error-surface'),
          success: rgb('feedback-success'),
          'success-surface': rgb('feedback-success-surface'),
          warning: rgb('feedback-warning'),
          'warning-surface': rgb('feedback-warning-surface'),
        },
      },
      // Type scale of the reference project; Tailwind's own sizes are kept.
      fontSize: {
        body: '16px',
        small: '12px',
        subtitle: '24px',
        title: '36px',
      },
      fontFamily: {
        // Poppins is self-hosted (@font-face in input.css); the system stack
        // is the fallback while it loads or if it fails.
        sans: [
          'Poppins',
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
