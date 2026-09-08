import type { FriendlyError } from "../lib/errors";

interface ErrorNoticeProps {
  error: FriendlyError;
  onRetry?: () => void;
}

export default function ErrorNotice({ error, onRetry }: ErrorNoticeProps) {
  return (
    <div className="error error-notice" role="alert">
      <p className="error-title">{error.title}</p>
      <p className="error-detail">{error.detail}</p>
      {error.raw && <p className="error-raw">GitHub said: {error.raw}</p>}
      {onRetry && (
        <button type="button" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}
