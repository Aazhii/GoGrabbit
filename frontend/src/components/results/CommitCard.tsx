import type { JSX } from "react";
import type { CommitItem } from "../../types/searchItems";
import { ActorChip, Badge, ExternalLink, Meta, RelTime } from "./primitives";
import { formatCount } from "./format";

export function CommitCard({ item }: { item: CommitItem }): JSX.Element {
  const message = item.message ?? "";
  const newline = message.indexOf("\n");
  const subject = (newline === -1 ? message : message.slice(0, newline)).trim();
  const body = newline === -1 ? "" : message.slice(newline + 1).trim();
  const shortSha = item.shortSha ?? item.sha.slice(0, 7);
  const repo = item.repository;

  return (
    <article className="rc-card">
      <div className="rc-head">
        <code className="rc-sha rc-sha-strong" title={item.sha}>
          {shortSha}
        </code>
        <h3 className="rc-title">
          <ExternalLink href={item.url}>{subject || shortSha}</ExternalLink>
        </h3>
        <div className="rc-badges">{item.isMerge ? <Badge tone="accent">merge</Badge> : null}</div>
      </div>

      {body ? (
        <details className="rc-commit-body">
          <summary>Show full message</summary>
          <pre>{body}</pre>
        </details>
      ) : null}

      {repo ? (
        <p className="rc-subline">
          {repo.url ? (
            <ExternalLink className="rc-repo-ref rc-link" href={repo.url}>
              {repo.fullName}
            </ExternalLink>
          ) : (
            <span className="rc-repo-ref">{repo.fullName}</span>
          )}
        </p>
      ) : null}

      <div className="rc-meta">
        <Meta>
          {item.author ? (
            <ActorChip actor={item.author} />
          ) : (
            <span className="rc-actor rc-actor-ghost" title={item.authorEmail ?? undefined}>
              {item.authorName?.trim() || item.authorEmail?.trim() || "unknown author"}
            </span>
          )}
        </Meta>
        <Meta>
          <RelTime iso={item.authoredAt} prefix="Authored" />
        </Meta>
        {item.committerName && item.committerName !== item.authorName ? (
          <Meta label="committed by">{item.committerName}</Meta>
        ) : null}
        {item.commentCount ? <Meta label="comments">{formatCount(item.commentCount)}</Meta> : null}
      </div>
    </article>
  );
}
