import { useRef, useState, type KeyboardEvent } from "react";
import GitHubSearchTab from "./components/GitHubSearchTab";
import WatchTab from "./components/WatchTab";

type Tab = "search" | "watch";

const TABS: { id: Tab; label: string }[] = [
  { id: "search", label: "Search" },
  { id: "watch", label: "Watch" },
];

export default function App() {
  const [tab, setTab] = useState<Tab>("search");
  const listRef = useRef<HTMLDivElement | null>(null);

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    event.preventDefault();
    const index = TABS.findIndex((t) => t.id === tab);
    const delta = event.key === "ArrowLeft" ? -1 : 1;
    const next = TABS[(index + delta + TABS.length) % TABS.length];
    if (!next) return;
    setTab(next.id);
    listRef.current?.querySelector<HTMLButtonElement>(`#tab-${next.id}`)?.focus();
  }

  return (
    <main>
      <header className="app-header">
        <h1>GoGrabbit</h1>
        <p className="subtitle">
          Search every corner of GitHub &mdash; and watch the repos worth coming back to.
        </p>
      </header>

      <div className="tabs" role="tablist" aria-label="Sections" ref={listRef} onKeyDown={handleKeyDown}>
        {TABS.map((entry) => {
          const selected = tab === entry.id;
          return (
            <button
              key={entry.id}
              type="button"
              role="tab"
              id={`tab-${entry.id}`}
              aria-selected={selected}
              aria-controls={`panel-${entry.id}`}
              tabIndex={selected ? 0 : -1}
              className={selected ? "tab tab-active" : "tab"}
              onClick={() => setTab(entry.id)}
            >
              {entry.label}
            </button>
          );
        })}
      </div>

      {tab === "search" ? (
        <div id="panel-search" role="tabpanel" aria-labelledby="tab-search">
          <GitHubSearchTab />
        </div>
      ) : (
        <div id="panel-watch" role="tabpanel" aria-labelledby="tab-watch">
          <WatchTab />
        </div>
      )}
    </main>
  );
}
