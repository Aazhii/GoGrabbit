import type { JSX, ReactNode } from "react";
import type { ActorRef } from "../../types/searchItems";
import { formatAbsolute, formatRelative } from "../../lib/relativeTime";
import { languageColor } from "./format";

export function Badge({
  children,
  tone = "neutral",
  title,
}: {
  children: ReactNode;
  tone?: "neutral" | "open" | "closed" | "warning" | "accent" | "danger" | "draft";
  title?: string;
}): JSX.Element {
  return (
    <span className={`rc-badge rc-badge-${tone}`} title={title}>
      {children}
    </span>
  );
}

/** A relative timestamp with the absolute value in a tooltip. */
export function RelTime({
  iso,
  prefix,
}: {
  iso: string | null | undefined;
  prefix?: string;
}): JSX.Element | null {
  const relative = formatRelative(iso);
  if (!relative) return null;
  return (
    <time dateTime={iso ?? undefined} title={formatAbsolute(iso)}>
      {prefix ? `${prefix} ${relative}` : relative}
    </time>
  );
}

/** One entry in a card's dense meta row. Renders nothing when empty. */
export function Meta({
  label,
  children,
}: {
  label?: string;
  children: ReactNode;
}): JSX.Element | null {
  if (children === null || children === undefined || children === false || children === "") {
    return null;
  }
  return (
    <span className="rc-meta-item">
      {label ? <span className="rc-meta-label">{label}</span> : null}
      {children}
    </span>
  );
}

export function LanguageDot({ language }: { language: string }): JSX.Element {
  return (
    <span className="rc-meta-item">
      <span className="rc-lang-dot" style={{ background: languageColor(language) }} aria-hidden />
      {language}
    </span>
  );
}

export function Avatar({
  src,
  alt,
  size = 20,
}: {
  src: string | null | undefined;
  alt: string;
  size?: number;
}): JSX.Element {
  if (!src) {
    return (
      <span
        className="rc-avatar rc-avatar-placeholder"
        style={{ width: size, height: size, fontSize: Math.round(size * 0.55) }}
        aria-hidden
      >
        {alt.slice(0, 1).toUpperCase()}
      </span>
    );
  }
  return (
    <img className="rc-avatar" src={src} alt="" width={size} height={size} loading="lazy" />
  );
}

/** Avatar + login, linked when GitHub gave us a URL. */
export function ActorChip({
  actor,
  fallback,
  size = 18,
}: {
  actor: ActorRef | null | undefined;
  fallback?: string | null;
  size?: number;
}): JSX.Element {
  if (!actor) {
    return (
      <span className="rc-actor rc-actor-ghost" title="No linked GitHub account">
        {fallback?.trim() || "unknown"}
      </span>
    );
  }
  const body = (
    <>
      <Avatar src={actor.avatarUrl} alt={actor.login} size={size} />
      <span>{actor.login}</span>
    </>
  );
  if (!actor.url) return <span className="rc-actor">{body}</span>;
  return (
    <a className="rc-actor" href={actor.url} target="_blank" rel="noreferrer">
      {body}
    </a>
  );
}

/** External link used for every card title. */
export function ExternalLink({
  href,
  className,
  children,
  title,
}: {
  href: string;
  className?: string;
  children: ReactNode;
  title?: string;
}): JSX.Element {
  return (
    <a className={className} href={href} target="_blank" rel="noreferrer" title={title}>
      {children}
    </a>
  );
}
