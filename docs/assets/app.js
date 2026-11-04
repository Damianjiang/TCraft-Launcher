(() => {
  "use strict";

  const nav = document.getElementById("nav");
  const progress = document.getElementById("progress");
  const burger = document.getElementById("burger");
  const mobileMenu = document.getElementById("mobileMenu");

  const onScroll = () => {
    const y = window.scrollY || document.documentElement.scrollTop;
    nav.classList.toggle("is-scrolled", y > 12);
    const max = document.documentElement.scrollHeight - window.innerHeight;
    progress.style.width = max > 0 ? `${Math.min(100, (y / max) * 100)}%` : "0%";
  };

  window.addEventListener("scroll", onScroll, { passive: true });
  onScroll();

  burger?.addEventListener("click", () => {
    const open = mobileMenu.classList.toggle("is-open");
    burger.setAttribute("aria-expanded", String(open));
  });

  mobileMenu?.querySelectorAll("a").forEach((a) => {
    a.addEventListener("click", () => {
      mobileMenu.classList.remove("is-open");
      burger?.setAttribute("aria-expanded", "false");
    });
  });

  // 入场动画
  const reveals = document.querySelectorAll("[data-reveal]");
  if ("IntersectionObserver" in window) {
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-in");
            io.unobserve(entry.target);
          }
        });
      },
      { rootMargin: "0px 0px -8% 0px", threshold: 0.08 }
    );
    reveals.forEach((el, i) => {
      el.style.transitionDelay = `${Math.min(i * 40, 180)}ms`;
      io.observe(el);
    });
  } else {
    reveals.forEach((el) => el.classList.add("is-in"));
  }

  // 卡片跟随光斑
  document.querySelectorAll("[data-spot]").forEach((card) => {
    card.addEventListener("pointermove", (e) => {
      const r = card.getBoundingClientRect();
      card.style.setProperty("--mx", `${e.clientX - r.left}px`);
      card.style.setProperty("--my", `${e.clientY - r.top}px`);
    });
  });

  // 截图切换
  const tabs = Array.from(document.querySelectorAll(".tab"));
  const shots = Array.from(document.querySelectorAll("#stage img"));
  tabs.forEach((tab, i) => {
    tab.addEventListener("click", () => {
      tabs.forEach((t) => {
        t.classList.remove("is-active");
        t.setAttribute("aria-selected", "false");
      });
      tab.classList.add("is-active");
      tab.setAttribute("aria-selected", "true");
      shots.forEach((img, j) => img.classList.toggle("is-active", i === j));
    });
  });

  // 年份
  const year = document.getElementById("year");
  if (year) year.textContent = String(new Date().getFullYear());

  // 完整版弹窗
  const modal = document.getElementById("modalFull");
  const openModal = () => {
    if (!modal) return;
    modal.hidden = false;
    document.body.style.overflow = "hidden";
    modal.querySelector(".modal__close")?.focus();
  };
  const closeModal = () => {
    if (!modal) return;
    modal.hidden = true;
    document.body.style.overflow = "";
  };

  document.querySelectorAll("[data-modal-open]").forEach((btn) => {
    btn.addEventListener("click", openModal);
  });
  modal?.querySelectorAll("[data-modal-close]").forEach((el) => {
    el.addEventListener("click", closeModal);
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && modal && !modal.hidden) closeModal();
  });

  // 复制 QQ 群号
  const copyBtn = document.getElementById("copyQq");
  copyBtn?.addEventListener("click", async () => {
    const qq = (document.getElementById("qqNumber")?.textContent || "").trim();
    try {
      await navigator.clipboard.writeText(qq);
    } catch {
      const ta = document.createElement("textarea");
      ta.value = qq;
      document.body.appendChild(ta);
      ta.select();
      document.execCommand("copy");
      ta.remove();
    }
    const original = copyBtn.textContent;
    copyBtn.textContent = copyBtn.dataset.copied || (original.includes("Copied") ? "Copied!" : "已复制");
    setTimeout(() => { copyBtn.textContent = original; }, 1600);
  });
})();
