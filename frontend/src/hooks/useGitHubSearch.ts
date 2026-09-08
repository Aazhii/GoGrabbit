import { useCallback, useEffect, useRef, useState } from "react";
import { runSearch } from "../api";
import { isAbort, toFriendlyError, type FriendlyError } from "../lib/errors";
import type { SearchRequestParams, SearchResponse, SearchTypeSlug } from "../types";

export interface LastRun {
  slug: SearchTypeSlug;
  params: SearchRequestParams;
}

export interface UseGitHubSearch {
  loading: boolean;
  error: FriendlyError | null;
  data: SearchResponse | null;
  /** What produced the results on screen — the source of truth for paging. */
  lastRun: LastRun | null;
  /** True until a search has been run for the currently selected type. */
  pristine: boolean;
  run: (slug: SearchTypeSlug, params: SearchRequestParams) => void;
  reset: () => void;
}

export function useGitHubSearch(): UseGitHubSearch {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<FriendlyError | null>(null);
  const [data, setData] = useState<SearchResponse | null>(null);
  const [lastRun, setLastRun] = useState<LastRun | null>(null);
  const [pristine, setPristine] = useState(true);

  // Same guard as useIssueSearch: every run aborts its predecessor and bumps a
  // monotonic id, so a slow earlier response can never overwrite a newer one
  // (switching type tabs quickly is exactly how that would otherwise happen).
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

  const run = useCallback((slug: SearchTypeSlug, params: SearchRequestParams) => {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    const runId = ++runIdRef.current;

    setPristine(false);
    setLastRun({ slug, params });
    setLoading(true);
    setError(null);

    runSearch(slug, params, controller.signal)
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
    setLastRun(null);
    setPristine(true);
  }, []);

  return { loading, error, data, lastRun, pristine, run, reset };
}
