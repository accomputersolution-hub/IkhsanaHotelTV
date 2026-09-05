/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./app/**/*.{js,ts,jsx,tsx}", "./components/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: {
          950: "#05070f",
          900: "#0a0e1a",
          800: "#111827",
          700: "#1a2236",
        },
        neon: {
          blue: "#3b82f6",
          cyan: "#22d3ee",
          violet: "#8b5cf6",
          purple: "#a855f7",
        },
      },
      fontFamily: {
        sans: ["var(--font-sans)", "system-ui", "sans-serif"],
        display: ["var(--font-display)", "Georgia", "serif"],
      },
      boxShadow: {
        glow: "0 0 40px rgba(59, 130, 246, 0.25)",
        "glow-violet": "0 0 40px rgba(139, 92, 246, 0.28)",
        glass: "0 8px 32px rgba(0, 0, 0, 0.35)",
      },
      backgroundImage: {
        "hero-grid":
          "linear-gradient(rgba(59,130,246,0.06) 1px, transparent 1px), linear-gradient(90deg, rgba(59,130,246,0.06) 1px, transparent 1px)",
      },
      backgroundSize: {
        grid: "48px 48px",
      },
    },
  },
  plugins: [],
};
