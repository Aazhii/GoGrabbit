import { useCallback, useEffect, useRef, useState } from "react";
import { getSearchCatalog } from "../api";
import { isAbort, toFriendlyError, type FriendlyError } from "../lib/errors";
import type { SearchCatalog } from "../types";

export interface UseSearchCatalog {
  loading: boolean;
  error: FriendlyError | null;
  catalog: SearchCatalog | null;
  reload: () => void;
}

/**
 * The catalog is the schema for the whole search UI — tabs, filter controls,
 * sorts — so it is fetched once on mount and everything else waits on it.
 */
export function useSearchCatalog(): UseSearchCatalog {
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<FriendlyError | null>(null);
  const [catalog, setCatalog] = useState<SearchCatalog | null>(null);
  const [attempt, setAttempt] = useState(0);

  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  // No synchronous setState in here: `loading` starts true and `reload` flips
  // it back on from the click that caused the refetch, so this effect only
  // ever writes state from the (async) fetch settling.
  useEffect(() => {
    const controller = new AbortController();

    getSearchCatalog(controller.signal)
      .then((data) => {
        if (!mountedRef.current) return;
        setCatalog(data);
        setLoading(false);
      })
      .catch((err: unknown) => {
        if (isAbort(err) || !mountedRef.current) return;
        setError(toFriendlyError(err));
        setLoading(false);
      });

    return () => controller.abort();
  }, [attempt]);

  const reload = useCallback(() => {
    setLoading(true);
    setError(null);
    setAttempt((n) => n + 1);
  }, []);

  return { loading, error, catalog, reload };
}
