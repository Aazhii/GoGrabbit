import { useEffect, useState } from "react";
import { createRepo, deleteRepo, listRecentIssues, listRepos, pollRepo } from "./api";
import type { SeenIssue, WatchedRepo } from "./types";

export default function App() {
  const [repos, setRepos] = useState<WatchedRepo[]>([]);
  const [issues, setIssues] = useState<SeenIssue[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [pollingId, setPollingId] = useState<string | null>(null);
  const [pollResult, setPollResult] = useState<string | null>(null);

  const [owner, setOwner] = useState("");
  const [repo, setRepo] = useState("");
  const [labels, setLabels] = useState("good first issue");
  const [intervalMinutes, setIntervalMinutes] = useState(30);

  async function refresh() {
    try {
      const [repoList, issueList] = await Promise.all([listRepos(), listRecentIssues()]);
      setRepos(repoList);
      setIssues(issueList);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }

  useEffect(() => {
    refresh();
  }, []);

  async function handleAddRepo(event: React.FormEvent) {
    event.preventDefault();
    try {
      await createRepo({
        owner: owner.trim(),
        repo: repo.trim(),
        labels: labels.split(",").map((l) => l.trim()).filter(Boolean),
        intervalMinutes,
      });
      setOwner("");
      setRepo("");
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }

  async function handleDelete(id: string) {
    try {
      await deleteRepo(id);
      await refresh();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }

  async function handlePoll(id: string) {
    setPollingId(id);
    setPollResult(null);
    try {
      const result = await pollRepo(id);
      await refresh();
      setPollResult(`Found ${result.newIssuesFound} new issue(s).`);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setPollingId(null);
    }
  }

  return (
    <main>
      <h1>GoGrabbit</h1>
      <p className="subtitle">Watch GitHub repos for good-first-issues before someone else grabs them.</p>

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
                      {r.owner}/{r.repo}
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
        <h2>Recently notified issues</h2>
        {issues.length === 0 ? (
          <p className="empty">Nothing found yet — add a repo and hit "Poll now".</p>
        ) : (
          <ul className="issue-list">
            {issues.map((issue) => (
              <li key={issue.id}>
                <a href={issue.url} target="_blank" rel="noreferrer">
                  {issue.title}
                </a>
                <span className="meta">
                  {issue.owner}/{issue.repo} · {new Date(issue.notifiedAt).toLocaleString()}
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  );
}
