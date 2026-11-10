(function () {
  const progress = document.getElementById("scroll-progress");
  if (progress) {
    window.addEventListener(
      "scroll",
      () => {
        const h = document.documentElement.scrollHeight - document.documentElement.clientHeight;
        const y = document.documentElement.scrollTop;
        progress.style.width = h > 0 ? `${(y / h) * 100}%` : "0%";
      },
      { passive: true }
    );
  }

  const revealEls = document.querySelectorAll(".reveal");
  if (revealEls.length && !window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (e.isIntersecting) {
            e.target.classList.add("is-visible");
            io.unobserve(e.target);
          }
        });
      },
      { rootMargin: "0px 0px -8% 0px", threshold: 0.08 }
    );
    revealEls.forEach((el) => io.observe(el));
  } else {
    revealEls.forEach((el) => el.classList.add("is-visible"));
  }

  const demos = [
    { cmd: "ls | grep chrome | head 2", out: "1  Chrome\n2  Chromium" },
    { cmd: "open 1", out: "Opening Chrome…" },
    { cmd: "browse weather addis", out: "(text results — pick with go 1, go 2…)" },
    { cmd: "ai how do I enable torch", out: "Plan: flash on → confirm with y" },
    { cmd: "timer 25m focus", out: "Timer armed (alarm clock)" },
    { cmd: "look color user #3dff8a", out: "Prompt colour updated" },
  ];

  const cmdEl = document.getElementById("term-cmd");
  const outEl = document.getElementById("term-out");
  const caretEl = document.getElementById("term-caret");

  if (!cmdEl || !outEl) return;

  let demoIndex = 0;
  let charIndex = 0;
  let phase = "type";
  let hold = 0;

  function tick() {
    const demo = demos[demoIndex];

    if (phase === "type") {
      charIndex++;
      cmdEl.textContent = demo.cmd.slice(0, charIndex);
      outEl.textContent = "";
      if (charIndex >= demo.cmd.length) {
        phase = "show";
        setTimeout(tick, 380);
        return;
      }
      setTimeout(tick, 42 + Math.random() * 28);
      return;
    }

    if (phase === "show") {
      outEl.textContent = demo.out;
      phase = "hold";
      hold = 0;
      setTimeout(tick, 2200);
      return;
    }

    if (phase === "hold") {
      phase = "clear";
      cmdEl.textContent = "";
      outEl.textContent = "";
      demoIndex = (demoIndex + 1) % demos.length;
      charIndex = 0;
      phase = "type";
      setTimeout(tick, 500);
    }
  }

  if (caretEl && !window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
    let caretOn = true;
    setInterval(() => {
      caretOn = !caretOn;
      caretEl.style.opacity = caretOn ? "0.85" : "0.15";
    }, 530);
    tick();
  } else {
    const d = demos[0];
    cmdEl.textContent = d.cmd;
    outEl.textContent = d.out;
    if (caretEl) caretEl.style.display = "none";
  }
})();
