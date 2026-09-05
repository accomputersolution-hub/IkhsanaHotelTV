(() => {
  const reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  if (reduce) {
    document.querySelectorAll(".reveal").forEach((el) => el.classList.add("is-in"));
    return;
  }

  const nodes = document.querySelectorAll(".reveal");
  if (!("IntersectionObserver" in window)) {
    nodes.forEach((el) => el.classList.add("is-in"));
    return;
  }

  const io = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("is-in");
          io.unobserve(entry.target);
        }
      });
    },
    { rootMargin: "0px 0px -8% 0px", threshold: 0.12 },
  );

  nodes.forEach((el) => io.observe(el));
})();
