import React, { useEffect, useState } from "react";
import {
  Activity,
  Sparkles,
  Play,
  Flame,
  RefreshCw,
  Terminal,
  ShieldAlert,
} from "lucide-react";

const API_BASE = (window.location.hostname === "localhost" ? "http://localhost:8080" : "https://traceguard-backend-09vb.onrender.com") + "/api/v1/incidents";

export default function App() {
  const [incidents, setIncidents] = useState([]);
  const [summary, setSummary] = useState({
    uniqueIncidentGroups: 0,
    totalRawEventsIngested: 0,
    deduplicationSavingsPercent: 0,
    openCriticalCount: 0,
    resolvedCount: 0,
  });
  const [selected, setSelected] = useState(null);
  const [replayPayload, setReplayPayload] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [sseConnected, setSseConnected] = useState(false);
  const [loadingAction, setLoadingAction] = useState(false);

  const fetchAll = async () => {
    try {
      const [listRes, sumRes] = await Promise.all([
        fetch(API_BASE),
        fetch(`${API_BASE}/summary`),
      ]);
      const listData = await listRes.json();
      const sumData = await sumRes.json();
      setIncidents(listData);
      setSummary(sumData);
      if (listData.length > 0 && !selected) {
        setSelected(listData[0]);
        setReplayPayload(listData[0].deadLetterPayload || "{}");
      }
    } catch (err) {
      console.error("Failed to fetch initial telemetry:", err);
    }
  };

  useEffect(() => {
    fetchAll();
    const eventSource = new EventSource(`${API_BASE}/stream`);
    eventSource.onopen = () => setSseConnected(true);
    eventSource.addEventListener("incident-update", (event) => {
      const updated = JSON.parse(event.data);
      setIncidents((prev) => {
        const exists = prev.some((i) => i.id === updated.id);
        const next = exists
          ? prev.map((i) => (i.id === updated.id ? updated : i))
          : [updated, ...prev];
        return next;
      });
      setSelected((curr) => (curr && curr.id === updated.id ? updated : curr));
      fetch(`${API_BASE}/summary`)
        .then((r) => r.json())
        .then(setSummary);
    });
    eventSource.onerror = () => setSseConnected(false);
    return () => eventSource.close();
  }, []);

  const triggerChaos = async () => {
    setLoadingAction(true);
    try {
      const res = await fetch(`${API_BASE}/simulate-chaos`, { method: "POST" });
      const data = await res.json();
      setIncidents(data);
      if (data.length > 0) {
        setSelected(data[0]);
        setReplayPayload(data[0].deadLetterPayload || "{}");
      }
      const sumRes = await fetch(`${API_BASE}/summary`);
      setSummary(await sumRes.json());
    } finally {
      setLoadingAction(false);
    }
  };

  const runAiTriage = async (id) => {
    setLoadingAction(true);
    try {
      const res = await fetch(`${API_BASE}/${id}/analyze`, { method: "POST" });
      const updated = await res.json();
      setSelected(updated);
    } finally {
      setLoadingAction(false);
    }
  };

  const replayWebhook = async (id) => {
    setLoadingAction(true);
    try {
      const res = await fetch(`${API_BASE}/${id}/replay`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          payload: replayPayload,
          targetEndpoint: selected.endpointPath,
        }),
      });
      const updated = await res.json();
      setSelected(updated);
    } finally {
      setLoadingAction(false);
    }
  };

  const filteredIncidents = incidents.filter((item) =>
    statusFilter === "ALL" ? true : item.status === statusFilter
  );

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 font-sans">
      <header className="border-b border-slate-800 bg-slate-900/70 backdrop-blur px-6 py-4 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg bg-indigo-500/20 border border-indigo-500/40 text-indigo-400">
            <ShieldAlert className="w-6 h-6" />
          </div>
          <div>
            <h1 className="text-lg font-bold tracking-tight flex items-center gap-2">
              TraceGuard Engine
              <span className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300 font-mono">
                Java 21 • Spring Boot 3.3 • SSE
              </span>
            </h1>
            <p className="text-xs text-slate-400">
              SHA-256 Stack-Trace Deduplication, Dead-Letter Webhook Replay & Gemini AI Root-Cause Triage
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <span
            className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-full text-xs font-medium border ${
              sseConnected
                ? "bg-emerald-950/60 border-emerald-700 text-emerald-300"
                : "bg-amber-950/60 border-amber-700 text-amber-300"
            }`}
          >
            <span
              className={`w-2 h-2 rounded-full ${
                sseConnected ? "bg-emerald-400 animate-ping" : "bg-amber-400"
              }`}
            />
            {sseConnected ? "SSE Stream Live" : "Connecting SSE..."}
          </span>

          <button
            onClick={triggerChaos}
            disabled={loadingAction}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-rose-600 hover:bg-rose-500 text-white text-sm font-semibold shadow-lg shadow-rose-950/50 cursor-pointer transition"
          >
            <Flame className="w-4 h-4" />
            Simulate Production Chaos
          </button>
        </div>
      </header>

      <section className="max-w-7xl mx-auto px-6 pt-6 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
          <p className="text-xs text-slate-400 uppercase">Raw Events Ingested</p>
          <p className="text-2xl font-bold mt-1">{summary.totalRawEventsIngested}</p>
          <p className="text-xs text-slate-500 mt-1">Across all microservices</p>
        </div>
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
          <p className="text-xs text-slate-400 uppercase">SHA-256 Deduplicated Groups</p>
          <p className="text-2xl font-bold mt-1 text-indigo-400">
            {summary.uniqueIncidentGroups}
          </p>
          <p className="text-xs text-emerald-400 mt-1">
            {summary.deduplicationSavingsPercent}% noise reduction
          </p>
        </div>
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
          <p className="text-xs text-slate-400 uppercase">Active Critical Incidents</p>
          <p className="text-2xl font-bold mt-1 text-rose-400">
            {summary.openCriticalCount}
          </p>
          <p className="text-xs text-slate-500 mt-1">Requires immediate triage</p>
        </div>
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-4">
          <p className="text-xs text-slate-400 uppercase">Replayed & Resolved</p>
          <p className="text-2xl font-bold mt-1 text-emerald-400">
            {summary.resolvedCount}
          </p>
          <p className="text-xs text-slate-500 mt-1">Verified via Dead-Letter Replay</p>
        </div>
      </section>

      <main className="max-w-7xl mx-auto px-6 py-6 grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-5 bg-slate-900 border border-slate-800 rounded-xl overflow-hidden flex flex-col">
          <div className="p-4 border-b border-slate-800 flex items-center justify-between">
            <h2 className="font-semibold text-sm flex items-center gap-2">
              <Activity className="w-4 h-4 text-indigo-400" />
              Deduplicated Incident Groups
            </h2>
            <div className="flex gap-1">
              {["ALL", "OPEN", "INVESTIGATING", "RESOLVED"].map((st) => (
                <button
                  key={st}
                  onClick={() => setStatusFilter(st)}
                  className={`px-2 py-1 rounded text-xs font-medium cursor-pointer ${
                    statusFilter === st
                      ? "bg-indigo-600 text-white"
                      : "bg-slate-800 text-slate-400 hover:text-slate-200"
                  }`}
                >
                  {st}
                </button>
              ))}
            </div>
          </div>

          <div className="divide-y divide-slate-800 max-h-[600px] overflow-y-auto">
            {filteredIncidents.length === 0 ? (
              <div className="p-8 text-center text-slate-400 text-sm">
                No incidents yet. Click <strong>"Simulate Production Chaos"</strong> in the top bar to fire realistic microservice exceptions!
              </div>
            ) : (
              filteredIncidents.map((item) => (
                <div
                  key={item.id}
                  onClick={() => {
                    setSelected(item);
                    setReplayPayload(item.deadLetterPayload || "{}");
                  }}
                  className={`p-4 cursor-pointer transition hover:bg-slate-800/60 ${
                    selected?.id === item.id ? "bg-slate-800/90 border-l-4 border-indigo-500" : ""
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="text-xs font-mono text-indigo-300 bg-indigo-950/70 px-2 py-0.5 rounded">
                      {item.serviceName}
                    </span>
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-bold px-2 py-0.5 rounded bg-slate-800 text-amber-300">
                        ×{item.occurrenceCount} spikes
                      </span>
                      <span
                        className={`text-[11px] font-semibold px-2 py-0.5 rounded ${
                          item.status === "RESOLVED"
                            ? "bg-emerald-950 text-emerald-300"
                            : item.status === "INVESTIGATING"
                            ? "bg-amber-950 text-amber-300"
                            : "bg-rose-950 text-rose-300"
                        }`}
                      >
                        {item.status}
                      </span>
                    </div>
                  </div>
                  <p className="text-sm font-semibold text-slate-100 mt-2 truncate">
                    {item.exceptionType}
                  </p>
                  <p className="text-xs text-slate-400 mt-1 line-clamp-1">
                    {item.errorMessage}
                  </p>
                  <div className="flex items-center justify-between mt-2 text-[11px] text-slate-500 font-mono">
                    <span>{item.endpointPath}</span>
                    <span>sha256:{item.fingerprint.slice(0, 10)}</span>
                  </div>
                </div>
              ))
            )}
          </div>
        </div>

        <div className="lg:col-span-7 space-y-6">
          {!selected ? (
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-12 text-center text-slate-400">
              Select an incident group on the left to inspect its stack fingerprint, run Gemini AI root-cause analysis, or replay its dead-letter payload.
            </div>
          ) : (
            <>
              <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4">
                <div className="flex flex-wrap items-start justify-between gap-4">
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-mono uppercase px-2 py-0.5 rounded bg-rose-950 text-rose-300 border border-rose-800">
                        {selected.severity}
                      </span>
                      <span className="text-xs font-mono text-slate-400">
                        Fingerprint: {selected.fingerprint}
                      </span>
                    </div>
                    <h3 className="text-base font-bold mt-2 text-white break-all">
                      {selected.exceptionType}
                    </h3>
                    <p className="text-xs text-slate-300 mt-1">{selected.errorMessage}</p>
                  </div>

                  <button
                    onClick={() => runAiTriage(selected.id)}
                    disabled={loadingAction}
                    className="inline-flex items-center gap-2 px-3.5 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold cursor-pointer transition"
                  >
                    <Sparkles className="w-4 h-4" />
                    {selected.aiRootCause ? "Re-Verify AI Diagnosis" : "Generate AI Root-Cause & Patch"}
                  </button>
                </div>

                <div>
                  <p className="text-xs font-semibold text-slate-400 mb-1.5 flex items-center gap-1.5">
                    <Terminal className="w-3.5 h-3.5" /> Normalized Stack Frame Sample
                  </p>
                  <pre className="bg-slate-950 border border-slate-800 rounded-lg p-3 text-xs font-mono text-slate-300 overflow-x-auto whitespace-pre-wrap">
                    {selected.sampleStackTrace}
                  </pre>
                </div>
              </div>

              {selected.aiRootCause && (
                <div className="bg-indigo-950/30 border border-indigo-800/70 rounded-xl p-5 space-y-3">
                  <div className="flex items-center justify-between">
                    <h4 className="text-sm font-bold text-indigo-300 flex items-center gap-2">
                      <Sparkles className="w-4 h-4" />
                      AI Root-Cause Diagnosis & Spring Boot Patch (Cached by SHA-256)
                    </h4>
                  </div>
                  <p className="text-xs text-slate-200 leading-relaxed">
                    {selected.aiRootCause}
                  </p>
                  <pre className="bg-slate-950 border border-indigo-900/60 rounded-lg p-3 text-xs font-mono text-emerald-300 overflow-x-auto">
                    {selected.aiSuggestedPatch}
                  </pre>
                </div>
              )}

              <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <h4 className="text-sm font-semibold flex items-center gap-2">
                      <RefreshCw className="w-4 h-4 text-emerald-400" />
                      Dead-Letter Payload Inspector & Replay
                    </h4>
                    <p className="text-xs text-slate-400">
                      Target Endpoint: <code className="text-indigo-300">{selected.endpointPath}</code>
                    </p>
                  </div>
                  {selected.lastReplayStatus && (
                    <span className="text-xs px-2.5 py-1 rounded bg-emerald-950 text-emerald-300 border border-emerald-800 font-mono">
                      Last Replay: HTTP {selected.lastReplayStatus} ({selected.lastReplayLatencyMs}ms)
                    </span>
                  )}
                </div>

                <textarea
                  rows={4}
                  value={replayPayload}
                  onChange={(e) => setReplayPayload(e.target.value)}
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg p-3 text-xs font-mono text-slate-200 focus:outline-none focus:border-indigo-500"
                />

                <div className="flex justify-end">
                  <button
                    onClick={() => replayWebhook(selected.id)}
                    disabled={loadingAction}
                    className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-semibold cursor-pointer transition"
                  >
                    <Play className="w-3.5 h-3.5" />
                    Replay Payload & Mark Resolved
                  </button>
                </div>
              </div>
            </>
          )}
        </div>
      </main>
    </div>
  );
}




