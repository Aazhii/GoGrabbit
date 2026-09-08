interface SpinnerProps {
  label?: string;
}

export default function Spinner({ label = "Loading…" }: SpinnerProps) {
  return (
    <p className="spinner-row">
      <span className="spinner" aria-hidden="true" />
      <span>{label}</span>
    </p>
  );
}
