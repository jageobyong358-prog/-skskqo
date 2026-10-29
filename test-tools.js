// 도시의 한 생애 — 테스트 서버 도구
// test.html에서만 불러온다. 본 게임(index.html)에는 들어가지 않는다.
// 게임 스크립트의 전역(s, EVENTS, ACTIONS, render 등)을 그대로 쓴다.
(() => {
  "use strict";
  const T = {};

  // ───── 무적 · 무한 정신력 ─────
  const baseHurt = hurt, baseStrain = strain, baseDie = die;
  T.god = false;
  T.calm = false;
  window.hurt = (n, cause) => (T.god ? undefined : baseHurt(n, cause));
  window.strain = (n, cause) => (T.calm ? undefined : baseStrain(n, cause));
  window.die = (cause, final) => (T.god ? say(`(테스트) 죽을 뻔했다: ${cause}`, "bad") : baseDie(cause, final));

  const BOSSES = [
    ["leiHeng", "뇌횡"], ["roachEmperor", "바퀴황제"], ["kali", "붉은 안개 칼리"],
    ["paleWhale", "창백한 고래"], ["marlinWhale", "청새치 고래"], ["redWhale", "새빨간 고래"],
  ];
  const JOBS = {
    "없음": null,
    "9급 해결사": { type: "fixer", grade: 9, exp: 0, color: null },
    "1급 해결사": { type: "fixer", grade: 1, exp: 20, color: null },
    "L사 사원": { type: "wing", co: "L사", rank: 0 },
    "N사 사원": { type: "wing", co: "N사", rank: 0 },
    "엄지 견습": { type: "finger", name: "엄지", rank: 0, exp: 0 },
    "대호수 선장": { type: "sailor", rank: 3, exp: 0, ship: "녹슨 닻" },
    "해적 선장": { type: "pirate", rank: 3, exp: 0, ship: "검은 물보라" },
    "금기사냥꾼": { type: "tabooHunter", rank: 0, exp: 0 },
    "혈귀 사냥꾼": { type: "bloodHunter", rank: 0, exp: 0 },
    "전투 집사": { type: "butler", house: "은빛 장미 저택", rank: 0 },
    "유적 탐사가": { type: "relicHunter", rank: 0, exp: 0 },
    "W사 정리요원": { type: "wclean", rank: 0 },
    "흑운회 와카슈": { type: "blackcloud", rank: 0 },
    "흑수 필두": { type: "heishou", branch: "묘", rank: 1, master: "가씨 가문의 로웰" },
    "가주": { type: "hhead", family: "가씨 가문" },
    "머리의 발톱": { type: "claw" },
  };
  const ORIGIN_LIST = [["back", "뒷골목"], ["nest", "둥지"], ["wing", "날개"], ["out", "외곽"], ["hcorp", "H사"], ["hatch", "R사 부화장"], ["spider", "거미집"]];

  const css = document.createElement("style");
  css.textContent = `
    .tt-toggle { position: fixed; right: calc(16px + env(safe-area-inset-right, 0px)); bottom: calc(16px + env(safe-area-inset-bottom, 0px)); z-index: 30;
      background: var(--blood); color: #fff; border: 0; border-radius: 3px; padding: 10px 14px; font-weight: 700; cursor: pointer; }
    .tt-panel { position: fixed; right: calc(16px + env(safe-area-inset-right, 0px)); bottom: calc(64px + env(safe-area-inset-bottom, 0px)); z-index: 30;
      width: min(360px, calc(100vw - 32px)); max-height: min(70vh, 640px); overflow-y: auto; background: var(--panel); border: 1px solid var(--blood);
      border-radius: 4px; padding: 14px; display: grid; gap: 12px; font-size: 13px; box-shadow: 0 10px 30px #0008; }
    .tt-panel h4 { margin: 0; font-size: 12px; letter-spacing: .14em; color: var(--muted); font-weight: 500; }
    .tt-row { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
    .tt-row .btn { padding: 6px 10px; font-size: 13px; }
    .tt-row input, .tt-row select { background: var(--bg); border: 1px solid var(--line-strong); border-radius: 3px; padding: 6px 8px; min-width: 0; }
    .tt-row input[type=number] { width: 72px; }
    .tt-row select { flex: 1; }
    .tt-row label { display: flex; align-items: center; gap: 6px; }
    .tt-note { color: var(--muted); font-size: 12px; margin: 0; }
    .tt-badge { position: fixed; left: calc(16px + env(safe-area-inset-left, 0px)); bottom: calc(16px + env(safe-area-inset-bottom, 0px)); z-index: 30;
      background: var(--blood); color: #fff; font-size: 11px; letter-spacing: .14em; padding: 4px 8px; border-radius: 2px; }
  `;
  document.head.appendChild(css);

  const badge = document.createElement("div");
  badge.className = "tt-badge";
  badge.textContent = "TEST SERVER";
  document.body.appendChild(badge);

  const toggle = document.createElement("button");
  toggle.className = "tt-toggle";
  toggle.id = "tt-toggle";
  toggle.textContent = "테스트 도구";
  document.body.appendChild(toggle);

  const panel = document.createElement("div");
  panel.className = "tt-panel";
  panel.hidden = true;
  document.body.appendChild(panel);
  toggle.addEventListener("click", () => { panel.hidden = !panel.hidden; if (!panel.hidden) draw(); });

  const need = () => { if (!s || !s.alive) { alertLine("먼저 태어나야 한다."); return false; } if (modalOpen) { alertLine("열린 창을 먼저 닫아야 한다."); return false; } return true; };
  let note = "";
  const alertLine = t => { note = t; draw(); };
  const apply = (fn, msg) => { if (!need()) return; fn(); save(); render(); alertLine(msg); };
  const runEvent = id => {
    if (!need()) return;
    const ev = EVENTS.find(e => e.id === id);
    if (!ev) return alertLine(`${id} 사건이 없다.`);
    yearEntry();
    queue = [ev];
    processQueue();
    alertLine(`「${id}」 사건을 실행했다.`);
  };

  function draw() {
    if (panel.hidden) return;
    const alive = s && s.alive;
    panel.innerHTML = `
      <div class="tt-row" style="justify-content:space-between"><b>테스트 도구</b><span class="tt-note">저장은 본 게임과 따로</span></div>
      ${note ? `<p class="tt-note" style="color:var(--fg)">${esc(note)}</p>` : ""}
      <h4>새 인생</h4>
      <div class="tt-row">
        <select id="tt-origin">${ORIGIN_LIST.map(([k, n]) => `<option value="${k}">${n}</option>`).join("")}</select>
        <button class="btn" id="tt-born">이 출생지로 태어난다</button>
      </div>
      <h4>상태 ${alive ? `· ${s.age}세 · 전투력 ${Math.round(power(s))}` : ""}</h4>
      <div class="tt-row">
        <label>나이 <input type="number" id="tt-age" min="0" max="120" value="${alive ? s.age : 20}"></label>
        <label>덕목 <input type="number" id="tt-virtue" min="0" max="130" value="${alive ? Math.round(s.fort) : 50}"></label>
        <button class="btn" id="tt-set">적용</button>
      </div>
      <div class="tt-row">
        <button class="btn" id="tt-heal">체력·정신 가득</button>
        <button class="btn" id="tt-money">+100,000안</button>
        <button class="btn" id="tt-ap">선택 횟수 채우기</button>
        <button class="btn" id="tt-skip10">10년 건너뛰기</button>
      </div>
      <div class="tt-row">
        <label><input type="checkbox" id="tt-god" ${T.god ? "checked" : ""}> 무적</label>
        <label><input type="checkbox" id="tt-calm" ${T.calm ? "checked" : ""}> 정신력 고정</label>
      </div>
      <h4>힘</h4>
      <div class="tt-row">
        <button class="btn" id="tt-ego">완전한 E.G.O</button>
        <button class="btn" id="tt-shin">신 100 · 각오 15</button>
        <button class="btn" id="tt-araya">아라야시키</button>
        <button class="btn" id="tt-tiger">맹호표탄 10발</button>
        <button class="btn" id="tt-star">별호(천강)</button>
      </div>
      <h4>직업</h4>
      <div class="tt-row">
        <select id="tt-job">${Object.keys(JOBS).map(k => `<option>${k}</option>`).join("")}</select>
        <button class="btn" id="tt-setjob">지정</button>
      </div>
      <h4>히든 보스</h4>
      <div class="tt-row">${BOSSES.map(([id, n]) => `<button class="btn tt-boss" data-id="${id}">${n}</button>`).join("")}</div>
      <p class="tt-note">고래는 선원·해적 직업일 때 실제 게임에 나온다. 여기서는 조건 없이 부른다.</p>
      <h4>사건 실행</h4>
      <div class="tt-row">
        <select id="tt-event">${EVENTS.map(e => `<option>${e.id}</option>`).join("")}</select>
        <button class="btn" id="tt-runev">실행</button>
      </div>
      <h4>유물</h4>
      <div class="tt-row">
        <select id="tt-relic">${RELICS.map(r => `<option>${esc(r.name)}</option>`).join("")}</select>
        <button class="btn" id="tt-give">지급</button>
      </div>`;

    const $t = id => panel.querySelector("#" + id);
    $t("tt-born").addEventListener("click", () => {
      if (modalOpen) return alertLine("열린 창을 먼저 닫아야 한다.");
      s = newLife(pick(NAMES), pick(["m", "f"]), $t("tt-origin").value);
      s.ap = apFor(s);
      say(`(테스트) ${where(s)}에서 태어났다.`, "event", "탄생");
      save(); render(); alertLine("새로 태어났다.");
    });
    $t("tt-set").addEventListener("click", () => apply(() => {
      s.age = clamp(+$t("tt-age").value || 0, 0, 120);
      const v = clamp(+$t("tt-virtue").value || 0, 0, 130);
      VIRTUES.forEach(([k]) => { s[k] = v; });
      s.ap = apFor(s); s.offer = null;
    }, "나이와 덕목을 바꿨다."));
    $t("tt-heal").addEventListener("click", () => apply(() => { s.hp = 100; s.sp = spMax(s); }, "체력과 정신력을 채웠다."));
    $t("tt-money").addEventListener("click", () => apply(() => { s.money += 100000; }, "100,000안을 넣었다."));
    $t("tt-ap").addEventListener("click", () => apply(() => { s.ap = apFor(s); s.offer = null; }, "이번 해의 선택 횟수를 채웠다."));
    $t("tt-skip10").addEventListener("click", () => {
      if (!need()) return;
      let n = 0;
      const step = () => {
        if (!s.alive || n >= 10) { render(); return alertLine(`${n}년을 건너뛰었다. 열린 창이 있으면 직접 고른다.`); }
        if (modalOpen) return alertLine(`${n}년째에 사건이 열렸다. 고른 뒤 다시 누르면 이어서 넘어간다.`);
        n++; s.ap = 0; advanceYear(); setTimeout(step, 0);
      };
      step();
    });
    $t("tt-god").addEventListener("change", e => { T.god = e.target.checked; alertLine(T.god ? "무적을 켰다." : "무적을 껐다."); });
    $t("tt-calm").addEventListener("change", e => { T.calm = e.target.checked; alertLine(T.calm ? "정신력이 줄지 않는다." : "정신력 고정을 껐다."); });
    $t("tt-ego").addEventListener("click", () => apply(() => { if (s.ego?.stable) return; bloomEgo(true, "(테스트)"); }, "완전한 E.G.O를 개화했다."));
    $t("tt-shin").addEventListener("click", () => apply(() => { s.shin = 100; s.resolve = Math.max(s.resolve || 0, 15); achieve("shin"); }, "신 100, 각오 15가 되었다. 망은 정신력이 버티는 만큼 쓸 수 있다."));
    $t("tt-araya").addEventListener("click", () => apply(() => { s.relic = "아라야시키"; s.memory = 100; }, "아라야시키를 쥐었다. 기억 100."));
    $t("tt-tiger").addEventListener("click", () => apply(() => { s.tigerShells = (s.tigerShells || 0) + 10; }, "맹호표탄 10발을 받았다."));
    $t("tt-star").addEventListener("click", () => apply(() => { s.birthStar = { tier: "tiangang", name: "천고성" }; s.starKnown = true; }, "36천강 천고성이 되었다."));
    $t("tt-setjob").addEventListener("click", () => apply(() => { const j = JOBS[$t("tt-job").value]; setJob(j ? JSON.parse(JSON.stringify(j)) : null); if (j?.type === "hhead") { s.hFamily = j.family; s.heir = { wins: 3, head: true }; } s.offer = null; }, `직업을 「${$t("tt-job").value}」(으)로 바꿨다.`));
    panel.querySelectorAll(".tt-boss").forEach(b => b.addEventListener("click", () => runEvent(b.dataset.id)));
    $t("tt-runev").addEventListener("click", () => runEvent($t("tt-event").value));
    $t("tt-give").addEventListener("click", () => apply(() => { const r = relicOf($t("tt-relic").value); if (!(s.relics || []).includes(r.name)) say(gainRelic(r), "good"); }, `「${$t("tt-relic").value}」을(를) 지급했다.`));
  }

  // 게임 화면이 다시 그려질 때 패널의 상태 표시도 맞춘다
  const baseRender = render;
  window.render = () => { baseRender(); draw(); };
})();
