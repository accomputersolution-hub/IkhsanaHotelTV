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
        void: {
          DEFAULT: "#0c0b0a",
          soft: "#161412",
          mute: "#1f1c19",
        },
        bone: {
          DEFAULT: "#efece6",
          dim: "#e2ddd4",
          mist: "#f7f5f1",
        },
        signal: {
          DEFAULT: "#1a9b8e",
          bright: "#2ec4b6",
          dim: "#147a70",
        },
        brass: {
          DEFAULT: "#c6a36b",
          soft: "#d8bc8a",
        },
      },
      fontFamily: {
        sans: ["var(--font-sans)", "system-ui", "sans-serif"],
        display: ["var(--font-display)", "Georgia", "serif"],
      },
      boxShadow: {
        soft: "0 20px 50px rgba(12, 11, 10, 0.18)",
        deep: "0 30px 80px rgba(12, 11, 10, 0.45)",
      },
      keyframes: {
        marquee: {
          "0%": { transform: "translateX(0)" },
          "100%": { transform: "translateX(-50%)" },
        },
        drift: {
          "0%, 100%": { transform: "translateY(0)" },
          "50%": { transform: "translateY(-10px)" },
        },
      },
      animation: {
        marquee: "marquee 32s linear infinite",
        drift: "drift 7s ease-in-out infinite",
      },
    },
  },
  plugins: [],
};
