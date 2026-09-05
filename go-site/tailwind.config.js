/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./app/**/*.{js,ts,jsx,tsx}",
    "./components/**/*.{js,ts,jsx,tsx}",
    "./lib/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        ink: {
          950: "#04060c",
          900: "#080c16",
          850: "#0c1220",
          800: "#121a2b",
          700: "#1c2740",
        },
        neon: {
          cyan: "#5eead4",
          blue: "#38bdf8",
          violet: "#a78bfa",
        },
        champagne: {
          DEFAULT: "#e8d5b5",
          dim: "#c4b09a",
        },
      },
      fontFamily: {
        sans: ["var(--font-sans)", "system-ui", "sans-serif"],
        display: ["var(--font-display)", "Georgia", "sans-serif"],
      },
      boxShadow: {
        glow: "0 0 50px rgba(56, 189, 248, 0.22)",
        "glow-cyan": "0 0 40px rgba(94, 234, 212, 0.22)",
        "glow-violet": "0 0 40px rgba(167, 139, 250, 0.22)",
        lift: "0 24px 80px rgba(0, 0, 0, 0.45)",
      },
      backgroundImage: {
        "hero-grid":
          "linear-gradient(rgba(94,234,212,0.045) 1px, transparent 1px), linear-gradient(90deg, rgba(94,234,212,0.045) 1px, transparent 1px)",
      },
      backgroundSize: {
        grid: "56px 56px",
      },
    },
  },
  plugins: [],
};
