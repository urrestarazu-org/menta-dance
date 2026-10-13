// Tailwind runs as a PostCSS plugin; Autoprefixer adds vendor prefixes (ADR-0024).
module.exports = {
  plugins: {
    tailwindcss: {},
    autoprefixer: {},
  },
};
