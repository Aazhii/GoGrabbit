interface PaginationProps {
  page: number;
  perPage: number;
  itemCount: number;
  hasNextPage: boolean;
  loading: boolean;
  onPageChange: (page: number) => void;
}

export default function Pagination({
  page,
  perPage,
  itemCount,
  hasNextPage,
  loading,
  onPageChange,
}: PaginationProps) {
  if (page <= 1 && !hasNextPage) return null;

  const first = (page - 1) * perPage + 1;
  const last = first + Math.max(itemCount - 1, 0);

  return (
    <nav className="pagination" aria-label="Search result pages">
      <button
        type="button"
        onClick={() => onPageChange(page - 1)}
        disabled={loading || page <= 1}
      >
        ← Previous
      </button>
      {/* GitHub's API gives no reliable page count once results are capped, so
          we state the range we actually delivered rather than "page X of Y". */}
      <span className="pagination-status">
        {itemCount > 0 ? `Showing ${first.toLocaleString()}–${last.toLocaleString()}` : `Page ${page}`}
      </span>
      <button
        type="button"
        onClick={() => onPageChange(page + 1)}
        disabled={loading || !hasNextPage}
      >
        Next →
      </button>
    </nav>
  );
}
