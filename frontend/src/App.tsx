import { useState } from "react";
import SearchForm from "./components/SearchForm";
import SearchResults from "./components/SearchResults";
import WatchTab from "./components/WatchTab";
import { useIssueSearch } from "./hooks/useIssueSearch";
import { DEFAULT_FORM_VALUES, toFilters, type SearchFormValues } from "./lib/searchForm";

type Tab = "search" | "watch";

export default function App() {
  const [tab, setTab] = useState<Tab>("search");
  const [values, setValues] = useState<SearchFormValues>(DEFAULT_FORM_VALUES);
  const search = useIssueSearch();

  function patchValues(patch: Partial<SearchFormValues>) {
    setValues((current) => ({ ...current, ...patch }));
  }

  function runSearch(page: number) {
    search.run(toFilters(values, page));
  }

  // Paging must reuse the filters the current results came from, not whatever
  // is in the form right now — the user may have edited it without searching.
  function goToPage(page: number) {
    const base = search.lastFilters ?? toFilters(values, page);
    search.run({ ...base, page });
  }

  function handleReset() {
    setValues(DEFAULT_FORM_VALUES);
    search.reset();
  }

  return (
    <main>
      <header className="app-header">
        <h1>GoGrabbit</h1>
        <p className="subtitle">
          Find a good first issue while it&rsquo;s still fresh — before someone else grabs it.
        </p>
      </header>

      <div className="tabs" role="tablist" aria-label="Sections">
        <button
          type="button"
          role="tab"
          id="tab-search"
          aria-selected={tab === "search"}
          aria-controls="panel-search"
          className={tab === "search" ? "tab tab-active" : "tab"}
          onClick={() => setTab("search")}
        >
          Search issues
        </button>
        <button
          type="button"
          role="tab"
          id="tab-watch"
          aria-selected={tab === "watch"}
          aria-controls="panel-watch"
          className={tab === "watch" ? "tab tab-active" : "tab"}
          onClick={() => setTab("watch")}
        >
          Watched repos
        </button>
      </div>

      {tab === "search" ? (
        <div id="panel-search" role="tabpanel" aria-labelledby="tab-search">
          <section>
            <h2>Search GitHub issues</h2>
            <SearchForm
              values={values}
              onChange={patchValues}
              onSubmit={() => runSearch(1)}
              onReset={handleReset}
              loading={search.loading}
            />
          </section>

          <SearchResults
            loading={search.loading}
            pristine={search.pristine}
            error={search.error}
            data={search.data}
            onRetry={() => {
              const last = search.lastFilters;
              if (last) search.run(last);
              else runSearch(1);
            }}
            onPageChange={goToPage}
          />
        </div>
      ) : (
        <div id="panel-watch" role="tabpanel" aria-labelledby="tab-watch">
          <WatchTab />
        </div>
      )}
    </main>
  );
}
