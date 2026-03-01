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

let network = null;

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
  const nodes = new vis.DataSet((payload.nodes || []).map((n) => ({
    id: n.id,
    label: n.title,
    color: n.kind === "path" ? "#c84c09" : "#5a6878",
    font: { color: "#13233a" },
    shape: "dot",
    size: n.kind === "path" ? 18 : 12
  })));
  const edges = new vis.DataSet((payload.edges || []).map((e, index) => ({
    id: index + 1,
    from: e.from,
    to: e.to,
    arrows: "to",
    color: e.kind === "path" ? "#c84c09" : "#93a4b8",
    width: e.kind === "path" ? 3 : 1
  })));
  const data = { nodes, edges };
  const options = {
    physics: {
      stabilization: true,
      barnesHut: { gravitationalConstant: -1800, springLength: 120, springConstant: 0.02 }
    },
    interaction: { hover: true, multiselect: false },
    layout: { improvedLayout: true }
  };
  if (network) {
    network.destroy();
  }
  network = new vis.Network(graphEl, data, options);
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
      `/api/${encodeURIComponent(wiki)}/route?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}&neighborhood=${Number.isFinite(neighborhood) ? neighborhood : 200}`
    );
    const path = payload.path || [];
    if (path.length === 0) {
      setStatus("No route found.");
      if (network) network.destroy();
      return;
    }
    routeTextEl.textContent = path.join(" -> ");
    renderGraph(payload);
    setStatus(`Found path (${path.length} nodes).`);
  } catch (err) {
    setStatus(`Error: ${err.message}`);
    if (network) network.destroy();
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
