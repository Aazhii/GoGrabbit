import { useEffect, useState } from "react";
import { createRepo, deleteRepo, listRecentIssues, listRepos, pollRepo } from "../api";
import type { SeenIssue, WatchedRepo } from "../types";

const SINCE_DAYS_OPTIONS = [
  { label: "Any time", value: "" },
  { label: "Last 24 hours", value: "1" },
  { label: "Last 3 days", value: "3" },
  { label: "Last 7 days", value: "7" },
  { label: "Last 14 days", value: "14" },
  { label: "Last 30 days", value: "30" },
];

function repoKey(r: { owner: string; repo: string }) {
  return `${r.owner}/${r.repo}`;
}

export default function WatchTab() {
  const [repos, setRepos] = useState<WatchedRepo[]>([]);
  const [issues, setIssues] = useState<SeenIssue[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [pollingId, setPollingId] = useState<string | null>(null);
  const [pollResult, setPollResult] = useState<string | null>(null);

  const [owner, setOwner] = useState("");
  const [repo, setRepo] = useState("");
  const [labels, setLabels] = useState("good first issue");
  const [intervalMinutes, setIntervalMinutes] = useState(30);

  const [sinceDaysFilter, setSinceDaysFilter] = useState("");
  const [repoFilter, setRepoFilter] = useState("");

  async function refreshRepos() {
    try {
      setRepos(await listRepos());
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }

  async function refreshIssues() {
    try {
      const [filterOwner, filterRepo] = repoFilter ? repoFilter.split("/") : [undefined, undefined];
      setIssues(
        await listRecentIssues({
          sinceDays: sinceDaysFilter ? Number(sinceDaysFilter) : undefined,
          owner: filterOwner,
          repo: filterRepo,
        }),
      );
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }

  useEffect(() => {
    refreshRepos();
  }, []);

  useEffect(() => {
    refreshIssues();
  }, [sinceDaysFilter, repoFilter]);

  async function handleAddRepo(event: React.FormEvent) {
    event.preventDefault();
    try {
      const created = await createRepo({
        owner: owner.trim(),
        repo: repo.trim(),
        labels: labels.split(",").map((l) => l.trim()).filter(Boolean),
        intervalMinutes,
      });
      setOwner("");
      setRepo("");
      await refreshRepos();
      // Poll immediately so a freshly added repo is never silently empty —
      // see CLAUDE.md; do not remove this.
      await handlePoll(created.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }

  async function handleDelete(id: string) {
    try {
      await deleteRepo(id);
      await Promise.all([refreshRepos(), refreshIssues()]);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }

  async function handlePoll(id: string) {
    setPollingId(id);
    setPollResult(null);
    try {
      const result = await pollRepo(id);
      await refreshIssues();
      setPollResult(`Found ${result.newIssuesFound} new issue(s).`);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setPollingId(null);
    }
  }

  return (
    <>
      {error && <div className="error">{error}</div>}

      <section>
        <h2>Add a repo to watch</h2>
        <form onSubmit={handleAddRepo} className="repo-form">
          <input placeholder="owner (e.g. react)" value={owner} onChange={(e) => setOwner(e.target.value)} required />
          <input placeholder="repo (e.g. react)" value={repo} onChange={(e) => setRepo(e.target.value)} required />
          <input placeholder="labels, comma separated" value={labels} onChange={(e) => setLabels(e.target.value)} required />
          <input
            type="number"
            min={1}
            value={intervalMinutes}
            onChange={(e) => setIntervalMinutes(Number(e.target.value))}
            title="Poll interval (minutes)"
          />
          <button type="submit">Add</button>
        </form>
      </section>

      <section>
        <h2>Watched repos</h2>
        {pollResult && <p className="poll-result">{pollResult}</p>}
        {repos.length === 0 ? (
          <p className="empty">No repos watched yet.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Repo</th>
                <th>Labels</th>
                <th>Interval</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {repos.map((r) => (
                <tr key={r.id}>
                  <td>
                    <a href={`https://github.com/${r.owner}/${r.repo}`} target="_blank" rel="noreferrer">
                      {repoKey(r)}
                    </a>
                  </td>
                  <td>{r.labels.join(", ")}</td>
                  <td>{r.intervalMinutes}m</td>
                  <td className="actions">
                    <button onClick={() => handlePoll(r.id)} disabled={pollingId === r.id}>
                      {pollingId === r.id ? "Polling…" : "Poll now"}
                    </button>
                    <button onClick={() => handleDelete(r.id)} className="danger">
                      Remove
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section>
        <h2>Issues</h2>
        <div className="filter-bar">
          <label>
            Posted within
            <select value={sinceDaysFilter} onChange={(e) => setSinceDaysFilter(e.target.value)}>
              {SINCE_DAYS_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            Repo
            <select value={repoFilter} onChange={(e) => setRepoFilter(e.target.value)}>
              <option value="">All repos</option>
              {repos.map((r) => (
                <option key={r.id} value={repoKey(r)}>
                  {repoKey(r)}
                </option>
              ))}
            </select>
          </label>
        </div>

        {issues.length === 0 ? (
          <p className="empty">
            {sinceDaysFilter || repoFilter
              ? "No issues match these filters."
              : "Nothing found yet — add a repo and hit \"Poll now\"."}
          </p>
        ) : (
          <ul className="issue-list">
            {issues.map((issue) => (
              <li key={issue.id}>
                <a href={issue.url} target="_blank" rel="noreferrer">
                  {issue.title}
                </a>
                <span className="meta">
                  {issue.owner}/{issue.repo} · posted {new Date(issue.postedAt).toLocaleDateString()} · notified{" "}
                  {new Date(issue.notifiedAt).toLocaleString()}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
