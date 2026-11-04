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

  // 完整版弹窗（事件委托：即使上方任一模块初始化失败也能工作）
  const modal = document.getElementById("modalFull");
  const openModal = () => {
    if (!modal) return;
    modal.hidden = false;
    document.body.style.overflow = "hidden";
  };
  const closeModal = () => {
    if (!modal) return;
    modal.hidden = true;
    document.body.style.overflow = "";
  };

  document.addEventListener("click", (e) => {
    const t = e.target instanceof Element ? e.target : null;
    if (!t) return;
    if (t.closest("[data-modal-open]")) {
      e.preventDefault();
      openModal();
    } else if (t.closest("[data-modal-close]")) {
      e.preventDefault();
      closeModal();
    }
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

  // 最新 Release 信息：页面不绑定任何版本号，规格全部从 GitHub 动态读取。
  // 下载按钮本身指向 /releases/latest/download/<固定资产名>，即使接口不可用也能下载最新版。
  (async () => {
    const API = "https://api.github.com/repos/Damianjiang/TCraft-Launcher/releases/latest";
    const isEn = (document.documentElement.lang || "").toLowerCase().startsWith("en");
    const fallback = isEn ? "See release page" : "见 Release 页面";
    const set = (key, val) => document.querySelectorAll(`[data-release="${key}"]`)
      .forEach((el) => { if (val) el.textContent = val; });
    const fmtSize = (n) => `${(n / 1048576).toFixed(2)} MiB`;
    const fmtDate = (s) => {
      const d = new Date(s);
      return isNaN(d) ? "" : `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
    };

    let info = null;
    try {
      const cached = sessionStorage.getItem("tcl-latest-release");
      if (cached) info = JSON.parse(cached);
    } catch {}
    if (!info) {
      try {
        const res = await fetch(API, { headers: { Accept: "application/vnd.github+json" } });
        if (!res.ok) throw new Error(String(res.status));
        const raw = await res.json();
        info = {
          published_at: raw.published_at,
          body: raw.body || "",
          assets: (raw.assets || []).map((a) => ({
            name: a.name,
            size: a.size,
            updated_at: a.updated_at
          }))
        };
        try { sessionStorage.setItem("tcl-latest-release", JSON.stringify(info)); } catch {}
      } catch { info = null; }
    }
    if (!info) {
      set("size", "—");
      set("date", "—");
      set("sha", fallback);
      return;
    }
    const asset = (info.assets || []).find((a) => /\.exe$/i.test(a.name)) || info.assets[0];
    if (asset) {
      set("size", fmtSize(asset.size));
      set("date", fmtDate(asset.updated_at || info.published_at));
    }
    const hash = /\b([a-f0-9]{64})\b/i.exec(info.body || "");
    set("sha", hash ? hash[1] : fallback);
  })();
})();
