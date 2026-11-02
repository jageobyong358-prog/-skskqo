"use strict";
// Files: the game's assets on the phone. The work is done in Java (FilesActivity, unity/*), this is the page.

const $ = (s) => document.querySelector(s);
const esc = (s) => String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const fmtSize = (n) => n >= 1 << 30 ? (n / (1 << 30)).toFixed(1) + " GB" : n >= 1 << 20 ? (n / (1 << 20)).toFixed(1) + " MB" : n >= 1024 ? Math.round(n / 1024) + " KB" : n + " B";
const OBJ = (f, s, p, m) => `https://la.files/obj?f=${encodeURIComponent(f)}&s=${s}&p=${encodeURIComponent(p)}&m=${m}&t=${Date.now()}`;

const pending = new Map();
let seq = 1;
function call(name, ...args) {
  return new Promise((resolve, reject) => {
    const id = seq++;
    pending.set(id, { resolve, reject });
    try { Native[name](id, ...args); } catch (e) { pending.delete(id); reject(e); }
  });
}

const listeners = {};
window.LA = {
  done(id, err, value) {
    const p = pending.get(id);
    if (!p) return;
    pending.delete(id);
    err ? p.reject(new Error(err)) : p.resolve(value);
  },
  event(name, data) { (listeners[name] || []).forEach((f) => f(data)); },
  back() {
    if (!$("#sheet").classList.contains("hidden")) { closeSheet(); return true; }
    if (stack.length > 1) { stack.pop(); render(); return true; }
    return false;
  },
};
const on = (name, f) => (listeners[name] = listeners[name] || []).push(f);

let toastTimer = 0;
function toast(msg, ms = 2600) {
  const t = $("#toast");
  t.textContent = msg;
  t.classList.remove("hidden");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.add("hidden"), ms);
}

const info = JSON.parse(Native.info());
// views: {v:"home"} | {v:"files", source, files} | {v:"bundle", file, data}
const stack = [{ v: "home" }];
const view = () => stack[stack.length - 1];
const push = (s) => { stack.push(s); render(); window.scrollTo(0, 0); };

$("#back").onclick = () => { if (!LA.back()) Native.close(); };

function render() {
  const s = view();
  if (s.v === "home") return renderHome();
  if (s.v === "files") return renderFiles(s);
  if (s.v === "bundle") return renderBundle(s);
}

// ---- home: the folders -----------------------------------------------------------------------

function renderHome() {
  $("#title").textContent = "파일";
  $("#subtitle").textContent = "게임 파일 탐색 · Files";
  const sources = JSON.parse(Native.sources());
  const blocked = info.sdk >= 33;
  $("#view").innerHTML = `
    <div class="card">
      <p>림버스 컴퍼니의 게임 파일(에셋 번들)을 열어 텍스처 · 스프라이트 · 텍스트 · 소리 · 영상 · 폰트를 보고 저장합니다.
      PC 앱의 <b>Files</b> 탭과 같은 기능입니다.</p>
      <p class="dim small">게임 파일이 있는 폴더를 한 번 연결하면 다음부터는 바로 열 수 있습니다.</p>
    </div>

    <h2>연결된 폴더</h2>
    ${sources.length ? `<ul class="list">${sources.map((x, i) => `
      <li class="tap" data-src="${i}"><div class="main"><b>${esc(x.name.split("/").pop() || x.name)}</b><span>${esc(x.name)}</span></div>
      <button class="icon" data-del="${i}" aria-label="연결 해제">×</button></li>`).join("")}</ul>`
      : `<p class="dim">아직 없습니다.</p>`}

    <h2>폴더 연결</h2>
    <div class="card">
      <b>휴대폰에 설치된 게임${info.gameInstalled ? " (설치됨)" : ""}</b>
      <p class="small dim">게임이 내려받은 파일은 <code>Android/data/${esc(info.game)}</code> 에 있습니다.</p>
      ${blocked ? `<p class="small">안드로이드 ${esc(info.release)}에서는 시스템이 다른 앱의 <code>Android/data</code> 폴더를 막아 두어서,
        아래 버튼이 그 폴더를 열지 못할 수 있습니다. 그럴 때는:</p>
        <ol class="steps small">
          <li>PC에 USB로 연결해 그 폴더를 휴대폰의 <code>Download</code> 등으로 복사하거나,</li>
          <li>Shizuku를 지원하는 파일 관리자(예: MiXplorer, ZArchiver)로 복사한 뒤</li>
          <li>복사한 폴더를 <b>다른 폴더 선택</b>으로 연결하세요.</li>
        </ol>` : `<p class="small">선택 화면이 열리면 아래쪽의 <b>이 폴더 사용</b> → <b>허용</b>을 누르세요.</p>`}
      <div class="row"><button class="primary" id="add-game">게임 폴더 연결</button></div>
    </div>
    <div class="card">
      <b>다른 폴더 선택</b>
      <p class="small dim">PC에서 복사해 온 파일도 됩니다: 설치 폴더의 <code>LimbusCompany_Data</code>, 또는 내려받은 번들이 있는
        <code>%USERPROFILE%\\AppData\\LocalLow\\Unity\\ProjectMoon_LimbusCompany</code>.</p>
      <div class="row"><button id="add-any">폴더 선택</button><button id="pick-files">파일 직접 열기</button></div>
    </div>`;
  $("#add-game").onclick = () => Native.addFolder(true);
  $("#add-any").onclick = () => Native.addFolder(false);
  $("#pick-files").onclick = () => Native.pickFiles();
  document.querySelectorAll("[data-src]").forEach((li) => li.onclick = (e) => {
    if (e.target.closest("[data-del]")) return;
    openSource(sources[+li.dataset.src]);
  });
  document.querySelectorAll("[data-del]").forEach((b) => b.onclick = () => {
    const x = sources[+b.dataset.del];
    if (confirm(`'${x.name}' 연결을 해제할까요?`)) { Native.removeSource(x.uri); renderHome(); }
  });
}

on("picked", (uri) => {
  if (!uri) return;
  const x = JSON.parse(Native.sources()).find((s) => s.uri === uri);
  if (view().v === "home") renderHome();
  if (x) openSource(x);
});
on("pickedFiles", (files) => {
  if (!files || !files.length) return;
  push({ v: "files", source: { name: "직접 연 파일" }, files, q: "", all: true, limit: 200 });
});

let scanning = false;
async function openSource(src) {
  if (scanning) return;
  scanning = true;
  $("#view").insertAdjacentHTML("afterbegin", `<p class="progress" id="scan-progress"><span class="spinner"></span>폴더를 읽는 중…</p>`);
  window.scrollTo(0, 0);
  try {
    const files = await call("scan", src.uri);
    if (!files.length) {
      toast("이 폴더에서 파일을 찾지 못했습니다 (접근이 막혔을 수 있습니다)", 5000);
      renderHome();
      return;
    }
    push({ v: "files", source: src, files, q: "", all: false, limit: 200 });
  } catch (e) {
    toast("폴더를 읽지 못했습니다: " + e.message, 5000);
    renderHome();
  } finally {
    scanning = false;
  }
}
on("scanning", (d) => { const p = $("#scan-progress"); if (p) p.innerHTML = `<span class="spinner"></span>파일 ${d.files}개 찾는 중…`; });

// ---- files of a folder -------------------------------------------------------------------------

function renderFiles(s) {
  $("#title").textContent = s.source.name.split("/").pop() || s.source.name;
  $("#subtitle").textContent = `파일 ${s.files.length}개`;
  $("#view").innerHTML = `
    <input type="search" id="q" placeholder="이름으로 찾기 (예: ui, sprite, story)" value="${esc(s.q)}">
    <div class="row small" style="margin-top:8px">
      <label class="row grow"><input type="checkbox" id="all" ${s.all ? "checked" : ""}> 모든 파일 보기</label>
      <span class="dim" id="count"></span>
    </div>
    <ul class="list" id="files"></ul>`;
  const draw = () => {
    const q = s.q.trim().toLowerCase();
    const shown = s.files.filter((f) => (s.all || f.unity) && (!q || f.display.toLowerCase().includes(q) || f.dir.toLowerCase().includes(q)));
    shown.sort((a, b) => a.display.localeCompare(b.display));
    $("#count").textContent = `${shown.length}개`;
    $("#files").innerHTML = shown.slice(0, s.limit).map((f, i) => `
      <li class="tap" data-i="${i}"><div class="main"><b>${esc(f.display)}</b><span>${esc(f.dir ? f.dir + "/" : "")}${esc(f.name)}</span></div>
      <div class="side">${fmtSize(f.size)}</div></li>`).join("")
      + (shown.length > s.limit ? `<li><button class="more" id="more">${shown.length - s.limit}개 더 보기</button></li>` : "");
    document.querySelectorAll("#files [data-i]").forEach((li) => li.onclick = () => openBundle(shown[+li.dataset.i]));
    const more = $("#more");
    if (more) more.onclick = () => { s.limit += 300; draw(); };
  };
  $("#q").oninput = (e) => { s.q = e.target.value; s.limit = 200; draw(); };
  $("#all").onchange = (e) => { s.all = e.target.checked; draw(); };
  draw();
}

let opening = false;
async function openBundle(f) {
  if (opening) return;
  opening = true;
  toast("여는 중… " + f.display, 60000);
  try {
    const data = await call("open", f.id);
    $("#toast").classList.add("hidden");
    push({ v: "bundle", file: f, data, type: "", q: "", limit: 300 });
  } catch (e) {
    toast("열 수 없습니다: " + e.message, 5000);
  } finally {
    opening = false;
  }
}

// ---- inside a bundle ---------------------------------------------------------------------------

const ORDER = ["Texture2D", "Sprite", "TextAsset", "AudioClip", "VideoClip", "Font", "MonoBehaviour", "Material", "Shader", "Mesh"];

function renderBundle(s) {
  const d = s.data;
  $("#title").textContent = s.file.display;
  $("#subtitle").textContent = `오브젝트 ${d.objects.length}개 · ${fmtSize(d.size)}`;
  const counts = {};
  d.objects.forEach((o) => counts[o.t] = (counts[o.t] || 0) + 1);
  const types = Object.keys(counts).sort((a, b) => {
    const ia = ORDER.indexOf(a), ib = ORDER.indexOf(b);
    return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib) || a.localeCompare(b);
  });
  const sf = d.files.map((f) => `${esc(f.name)} · v${f.version} · ${esc(f.unity)}${f.typeTree ? "" : " · 타입 트리 없음"}`).join("<br>");
  $("#view").innerHTML = `
    <p class="dim small">${sf}</p>
    ${d.errors.length ? `<p class="err small">${esc(d.errors.join("\n"))}</p>` : ""}
    <input type="search" id="q" placeholder="이름 · 경로로 찾기" value="${esc(s.q)}">
    <div class="chips"><button class="chip ${s.type ? "" : "on"}" data-t="">전체 ${d.objects.length}</button>
      ${types.map((t) => `<button class="chip ${s.type === t ? "on" : ""}" data-t="${esc(t)}">${esc(t)} ${counts[t]}</button>`).join("")}</div>
    <div class="row"><span class="dim small grow" id="count"></span><button id="save-all">보이는 것 모두 저장</button></div>
    <p class="progress" id="save-progress"></p>
    <ul class="list" id="objs"></ul>`;
  let shown = [];
  const draw = () => {
    const q = s.q.trim().toLowerCase();
    shown = d.objects.filter((o) => (!s.type || o.t === s.type) && (!q || (o.n || "").toLowerCase().includes(q) || (o.c || "").toLowerCase().includes(q) || o.p.includes(q)));
    shown.sort((a, b) => (ORDER.indexOf(a.t) < 0 ? 99 : ORDER.indexOf(a.t)) - (ORDER.indexOf(b.t) < 0 ? 99 : ORDER.indexOf(b.t)) || (a.n || "").localeCompare(b.n || ""));
    $("#count").textContent = `${shown.length}개`;
    $("#objs").innerHTML = shown.slice(0, s.limit).map((o, i) => `
      <li class="tap" data-i="${i}"><span class="badge ${esc(o.t)}">${esc(o.t)}</span>
      <div class="main"><b>${esc(o.n || "(이름 없음)")}</b><span>${esc(o.c || "#" + o.p)}</span></div>
      <div class="side">${fmtSize(o.z)}</div></li>`).join("")
      + (shown.length > s.limit ? `<li><button class="more" id="more">${shown.length - s.limit}개 더 보기</button></li>` : "");
    document.querySelectorAll("#objs [data-i]").forEach((li) => li.onclick = () => preview(s, shown[+li.dataset.i]));
    const more = $("#more");
    if (more) more.onclick = () => { s.limit += 300; draw(); };
  };
  $("#q").oninput = (e) => { s.q = e.target.value; s.limit = 300; draw(); };
  document.querySelectorAll(".chip").forEach((c) => c.onclick = () => { s.type = c.dataset.t; s.limit = 300; renderBundle(s); });
  $("#save-all").onclick = async () => {
    if (!shown.length) return;
    if (!confirm(`${shown.length}개를 저장할까요?\n(텍스처는 PNG, 텍스트는 원본, 나머지는 JSON)\n→ Downloads/LimbusArchive/Files/${s.file.display}`)) return;
    $("#save-all").disabled = true;
    try {
      const r = await call("saveMany", s.file.id, JSON.stringify(shown.map((o) => o.s + ":" + o.p)), "Files/" + s.file.display);
      toast(`${r.ok}개 저장${r.failed ? `, ${r.failed}개 실패` : ""} → ${r.folder}`, 5000);
    } catch (e) {
      toast("저장 실패: " + e.message, 5000);
    } finally {
      const b = $("#save-all");
      if (b) b.disabled = false;
      const p = $("#save-progress");
      if (p) p.textContent = "";
    }
  };
  draw();
}
on("saving", (d) => { const p = $("#save-progress"); if (p) p.innerHTML = `<span class="spinner"></span>저장 중 ${d.done} / ${d.total}`; });
on("indexing", (d) => {
  const c = $("#p-content .progress");
  if (c) c.innerHTML = `<span class="spinner"></span>다른 번들에서 텍스처를 찾는 중… ${d.done} / ${d.total}`;
});

// ---- preview -------------------------------------------------------------------------------

let current = null;
function closeSheet() {
  $("#sheet").classList.add("hidden");
  $("#p-content").innerHTML = "";
  current = null;
}
$("#p-close").onclick = closeSheet;
$("#sheet").onclick = (e) => { if (e.target.id === "sheet") closeSheet(); };

async function preview(s, o) {
  current = { s, o };
  $("#p-name").textContent = o.n || "(이름 없음)";
  $("#p-meta").textContent = `${o.t} · #${o.p}${o.c ? " · " + o.c : ""}`;
  $("#p-content").innerHTML = `<p class="progress"><span class="spinner"></span>읽는 중…</p>`;
  $("#sheet").classList.remove("hidden");
  $("#p-save").disabled = true;
  const mine = current;
  try {
    const r = await call("preview", s.file.id, o.s, o.p);
    if (current !== mine) return;
    const url = OBJ(s.file.id, o.s, o.p, "export");
    const note = r.note ? `<p class="dim small">${esc(r.note)}</p>` : "";
    let html;
    if (r.kind === "image") html = `<img src="${url}" alt="">${note}`;
    else if (r.kind === "audio") html = `<audio controls autoplay src="${url}"></audio>${note}`;
    else if (r.kind === "video") html = `<video controls autoplay playsinline src="${url}"></video>${note}`;
    else if (r.kind === "font") {
      const fam = "f" + Date.now();
      html = `<style>@font-face{font-family:${fam};src:url("${url}")}</style>
        <p style="font-family:${fam};font-size:26px;line-height:1.4">Limbus Company 림버스 컴퍼니<br>0123456789 ABCDEFG abcdefg<br>다람쥐 헌 쳇바퀴에 타고파</p>${note}`;
    } else if (r.kind === "text" || r.kind === "json") {
      html = `${note}<pre>${esc(r.kind === "json" ? prettyMaybe(r.text) : r.text)}</pre>${r.cut ? `<p class="dim small">… 일부만 보여 줍니다 (저장하면 전체)</p>` : ""}`;
    } else {
      html = `<p>${esc(r.note || "미리 볼 수 없는 형식입니다.")}</p><p class="dim small">${fmtSize(r.size)} · 저장하면 ${esc(r.fileName)}</p>`;
    }
    $("#p-content").innerHTML = html;
    $("#p-save").textContent = `저장 (${(r.fileName.split(".").pop() || "").toUpperCase()})`;
    $("#p-save").disabled = false;
  } catch (e) {
    if (current !== mine) return;
    $("#p-content").innerHTML = `<p class="err">${esc(e.message)}</p><p class="dim small">JSON이나 원본으로는 저장할 수 있습니다.</p>`;
  }
}

function prettyMaybe(t) {
  if (t.length > 200000) return t;
  try { return JSON.stringify(JSON.parse(t), null, 1); } catch (e) { return t; }
}

async function saveCurrent(mode) {
  if (!current) return;
  const { s, o } = current;
  try {
    const path = await call("save", s.file.id, o.s, o.p, mode, "Files/" + s.file.display);
    toast("저장했습니다 → " + path, 4000);
  } catch (e) {
    toast("저장 실패: " + e.message, 5000);
  }
}
$("#p-save").onclick = () => saveCurrent("export");
$("#p-json").onclick = () => saveCurrent("json");
$("#p-raw").onclick = () => saveCurrent("raw");

render();
