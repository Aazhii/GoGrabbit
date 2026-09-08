import { useCallback, useEffect, useRef, useState } from "react";
import { searchIssues } from "../api";
import { isAbort, toFriendlyError, type FriendlyError } from "../lib/errors";
import type { IssueSearchFilters, IssueSearchResponse } from "../types";

export interface UseIssueSearch {
  loading: boolean;
  error: FriendlyError | null;
  data: IssueSearchResponse | null;
  /** Filters used for the most recent run — the source of truth for paging. */
  lastFilters: IssueSearchFilters | null;
  /** True until the first search has been run in this session. */
  pristine: boolean;
  run: (filters: IssueSearchFilters) => void;
  reset: () => void;
}

export function useIssueSearch(): UseIssueSearch {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<FriendlyError | null>(null);
  const [data, setData] = useState<IssueSearchResponse | null>(null);
  const [lastFilters, setLastFilters] = useState<IssueSearchFilters | null>(null);
  const [pristine, setPristine] = useState(true);

  // Every run bumps the token and aborts the previous request, so a slow
  // earlier search can never land after — or overwrite — a newer one.
  const controllerRef = useRef<AbortController | null>(null);
  const runIdRef = useRef(0);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
      controllerRef.current?.abort();
    };
  }, []);

  const run = useCallback((filters: IssueSearchFilters) => {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    const runId = ++runIdRef.current;

    setPristine(false);
    setLastFilters(filters);
    setLoading(true);
    setError(null);

    searchIssues(filters, controller.signal)
      .then((response) => {
        if (runId !== runIdRef.current || !mountedRef.current) return;
        setData(response);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (isAbort(err)) return;
        if (runId !== runIdRef.current || !mountedRef.current) return;
        setError(toFriendlyError(err));
        setLoading(false);
      });
  }, []);

  const reset = useCallback(() => {
    controllerRef.current?.abort();
    runIdRef.current++;
    setLoading(false);
    setError(null);
    setData(null);
    setLastFilters(null);
    setPristine(true);
  }, []);

  return { loading, error, data, lastFilters, pristine, run, reset };
}
