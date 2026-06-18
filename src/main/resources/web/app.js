const wikiSelect = document.getElementById("wiki-select");
const startInput = document.getElementById("start-input");
const endInput = document.getElementById("end-input");
const startSuggestions = document.getElementById("start-suggestions");
const endSuggestions = document.getElementById("end-suggestions");
const neighborhoodInput = document.getElementById("neighbor-input");
const findRouteBtn = document.getElementById("find-route-btn");
const statusEl = document.getElementById("status");
const routeTextEl = document.getElementById("route-text");
const graphEl = document.getElementById("graph");

async function api(url) {
  const resp = await fetch(url);
  const json = await resp.json().catch(() => ({}));
  if (!resp.ok) {
    const message = json.error || `HTTP ${resp.status}`;
    throw new Error(message);
  }
  return json;
}

function setStatus(text) {
  statusEl.textContent = text;
}

async function loadWikis() {
  const data = await api("/api/wikis");
  wikiSelect.innerHTML = "";
  for (const wiki of data.wikis || []) {
    const opt = document.createElement("option");
    opt.value = wiki;
    opt.textContent = wiki;
    wikiSelect.appendChild(opt);
  }
}

async function updateSuggestions(inputEl, dataListEl) {
  const wiki = wikiSelect.value;
  const q = inputEl.value.trim();
  if (!wiki || q.length < 2) {
    dataListEl.innerHTML = "";
    return;
  }
  const data = await api(`/api/${encodeURIComponent(wiki)}/search?q=${encodeURIComponent(q)}&limit=12`);
  dataListEl.innerHTML = "";
  for (const title of data.results || []) {
    const option = document.createElement("option");
    option.value = title;
    dataListEl.appendChild(option);
  }
}

function renderGraph(payload) {
  const nodes = payload.nodes || [];
  const edges = payload.edges || [];
  const pathNodes = nodes.filter((n) => n.kind === "path");
  const neighborNodes = nodes.filter((n) => n.kind === "neighbor");

  const width = Math.max(graphEl.clientWidth, 640);
  const height = Math.max(graphEl.clientHeight, 420);
  const svgNs = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(svgNs, "svg");
  svg.setAttribute("width", `${width}`);
  svg.setAttribute("height", `${height}`);
  svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
  svg.setAttribute("class", "graph-svg");

  const nodePositions = new Map();
  const pathXStart = 80;
  const pathXEnd = Math.max(pathXStart + 1, width - 80);
  const pathY = Math.floor(height * 0.52);
  const pathStep = pathNodes.length > 1 ? (pathXEnd - pathXStart) / (pathNodes.length - 1) : 0;

  for (let i = 0; i < pathNodes.length; i++) {
    const n = pathNodes[i];
    nodePositions.set(n.id, { x: Math.round(pathXStart + i * pathStep), y: pathY, kind: "path", title: n.title });
  }

  const neighborsByPath = new Map();
  for (const edge of edges) {
    if (edge.kind !== "neighbor") continue;
    if (!neighborsByPath.has(edge.from)) {
      neighborsByPath.set(edge.from, []);
    }
    neighborsByPath.get(edge.from).push(edge.to);
  }

  const neighborIdSet = new Set(neighborNodes.map((n) => n.id));
  for (const pathNode of pathNodes) {
    const sourcePos = nodePositions.get(pathNode.id);
    const linked = neighborsByPath.get(pathNode.id) || [];
    for (let i = 0; i < linked.length; i++) {
      const neighborId = linked[i];
      if (!neighborIdSet.has(neighborId)) continue;
      if (nodePositions.has(neighborId)) continue;
      const row = Math.floor(i / 2) + 1;
      const yOffset = row * 62 * (i % 2 === 0 ? -1 : 1);
      nodePositions.set(neighborId, {
        x: sourcePos.x + (i % 3 === 0 ? -24 : 24),
        y: pathY + yOffset,
        kind: "neighbor",
        title: (neighborNodes.find((n) => n.id === neighborId) || { title: String(neighborId) }).title
      });
    }
  }

  const defs = document.createElementNS(svgNs, "defs");
  defs.innerHTML = `
    <marker id="arrow-path" markerWidth="10" markerHeight="10" refX="9" refY="3" orient="auto">
      <path d="M0,0 L0,6 L9,3 z" fill="#c84c09"></path>
    </marker>
    <marker id="arrow-neighbor" markerWidth="8" markerHeight="8" refX="7" refY="2.5" orient="auto">
      <path d="M0,0 L0,5 L7,2.5 z" fill="#93a4b8"></path>
    </marker>`;
  svg.appendChild(defs);

  for (const edge of edges) {
    const from = nodePositions.get(edge.from);
    const to = nodePositions.get(edge.to);
    if (!from || !to) continue;
    const line = document.createElementNS(svgNs, "line");
    line.setAttribute("x1", `${from.x}`);
    line.setAttribute("y1", `${from.y}`);
    line.setAttribute("x2", `${to.x}`);
    line.setAttribute("y2", `${to.y}`);
    line.setAttribute("stroke", edge.kind === "path" ? "#c84c09" : "#93a4b8");
    line.setAttribute("stroke-width", edge.kind === "path" ? "3" : "1.25");
    line.setAttribute("marker-end", edge.kind === "path" ? "url(#arrow-path)" : "url(#arrow-neighbor)");
    line.setAttribute("opacity", edge.kind === "path" ? "0.95" : "0.75");
    svg.appendChild(line);
  }

  for (const [id, pos] of nodePositions.entries()) {
    const g = document.createElementNS(svgNs, "g");
    g.setAttribute("data-id", `${id}`);
    const circle = document.createElementNS(svgNs, "circle");
    circle.setAttribute("cx", `${pos.x}`);
    circle.setAttribute("cy", `${pos.y}`);
    circle.setAttribute("r", pos.kind === "path" ? "11" : "7");
    circle.setAttribute("fill", pos.kind === "path" ? "#c84c09" : "#5a6878");
    g.appendChild(circle);

    const text = document.createElementNS(svgNs, "text");
    text.setAttribute("x", `${pos.x + 14}`);
    text.setAttribute("y", `${pos.y - 12}`);
    text.setAttribute("font-size", pos.kind === "path" ? "14" : "11");
    text.setAttribute("fill", "#13233a");
    text.textContent = pos.title;
    g.appendChild(text);
    svg.appendChild(g);
  }

  graphEl.innerHTML = "";
  graphEl.appendChild(svg);
}

async function findRoute() {
  const wiki = wikiSelect.value;
  const from = startInput.value.trim();
  const to = endInput.value.trim();
  const neighborhood = Number.parseInt(neighborhoodInput.value, 10);
  if (!wiki || !from || !to) {
    setStatus("Pick wiki, start and end.");
    return;
  }
  setStatus("Searching...");
  routeTextEl.textContent = "";
  try {
    const payload = await api(
      `/api/${encodeURIComponent(wiki)}/route?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&neighborhood=${Number.isFinite(neighborhood) ? neighborhood : 0}`
    );
    const path = payload.path || [];
    if (path.length === 0) {
      setStatus("No route found.");
      graphEl.innerHTML = "";
      return;
    }
    routeTextEl.textContent = path.join(" -> ");
    renderGraph(payload);
    setStatus(`Found path (${path.length} nodes).`);
  } catch (err) {
    setStatus(`Error: ${err.message}`);
    graphEl.innerHTML = "";
  }
}

function debounce(fn, waitMs) {
  let t = null;
  return (...args) => {
    if (t) clearTimeout(t);
    t = setTimeout(() => fn(...args), waitMs);
  };
}

const updateStartSuggestions = debounce(() => updateSuggestions(startInput, startSuggestions), 120);
const updateEndSuggestions = debounce(() => updateSuggestions(endInput, endSuggestions), 120);

startInput.addEventListener("input", updateStartSuggestions);
endInput.addEventListener("input", updateEndSuggestions);
findRouteBtn.addEventListener("click", findRoute);

loadWikis()
  .then(() => setStatus("Ready."))
  .catch((err) => setStatus(`Failed to load wikis: ${err.message}`));
