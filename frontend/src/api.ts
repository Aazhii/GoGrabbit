import type { SeenIssue, WatchedRepo } from "./types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });

  if (!response.ok) {
    const body = await response.text();
    let message = body;
    try {
      message = JSON.parse(body).message ?? body;
    } catch {
      // not JSON — fall back to raw body
    }
    throw new Error(`(${response.status}) ${message}`);
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export function listRepos(): Promise<WatchedRepo[]> {
  return request("/repos");
}

export function createRepo(input: {
  owner: string;
  repo: string;
  labels: string[];
  intervalMinutes: number;
}): Promise<WatchedRepo> {
  return request("/repos", { method: "POST", body: JSON.stringify(input) });
}

export function deleteRepo(id: string): Promise<void> {
  return request(`/repos/${id}`, { method: "DELETE" });
}

export function pollRepo(id: string): Promise<{ newIssuesFound: number }> {
  return request(`/repos/${id}/poll`, { method: "POST" });
}

export interface IssueFilters {
  sinceDays?: number;
  owner?: string;
  repo?: string;
}

export function listRecentIssues(filters: IssueFilters = {}): Promise<SeenIssue[]> {
  const params = new URLSearchParams();
  if (filters.sinceDays != null) params.set("sinceDays", String(filters.sinceDays));
  if (filters.owner) params.set("owner", filters.owner);
  if (filters.repo) params.set("repo", filters.repo);

  const query = params.toString();
  return request(`/issues/recent${query ? `?${query}` : ""}`);
}
