export interface WatchedRepo {
  id: string;
  owner: string;
  repo: string;
  labels: string[];
  intervalMinutes: number;
  active: boolean;
  createdAt: string;
}

export interface SeenIssue {
  id: string;
  owner: string;
  repo: string;
  title: string;
  url: string;
  postedAt: string;
  labeledAt: string;
  notifiedAt: string;
}
