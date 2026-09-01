import type { SeenIssue, WatchedRepo } from "./types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { "Content-Type": "application/json" },
    ...init,
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`${init?.method ?? "GET"} ${path} failed: ${response.status} ${body}`);
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

export function listRecentIssues(): Promise<SeenIssue[]> {
  return request("/issues/recent");
}
