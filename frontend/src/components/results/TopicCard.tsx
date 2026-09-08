import type { JSX } from "react";
import type { TopicItem } from "../../types/searchItems";
import { Badge, ExternalLink, Meta, RelTime } from "./primitives";
import { formatCount } from "./format";

export function TopicCard({ item }: { item: TopicItem }): JSX.Element {
  // The topic search payload carries no `url` of its own; GitHub's canonical
  // topic page is derived from the (URL-safe) topic name.
  // The backend supplies the canonical topic URL; fall back to deriving it so
  // an older backend response still renders a working link.
  const url =
    item.url ?? `https://github.com/topics/${encodeURIComponent(item.name)}`;

  return (
    <article className="rc-card rc-card-topic">
      {item.logoUrl ? (
        <img className="rc-topic-logo" src={item.logoUrl} alt="" width={40} height={40} loading="lazy" />
      ) : (
        <span className="rc-topic-logo rc-topic-logo-placeholder" aria-hidden>
          {(item.displayName ?? item.name).slice(0, 1).toUpperCase()}
        </span>
      )}
      <div className="rc-topic-body">
        <div className="rc-head">
          <h3 className="rc-title">
            <ExternalLink href={url}>{item.displayName?.trim() || item.name}</ExternalLink>
          </h3>
          <div className="rc-badges">
            {item.featured ? <Badge tone="accent">featured</Badge> : null}
            {item.curated ? <Badge tone="open">curated</Badge> : null}
          </div>
        </div>

        {item.displayName && item.displayName !== item.name ? (
          <p className="rc-subline">
            <span className="rc-repo-ref">{item.name}</span>
          </p>
        ) : null}

        {item.shortDescription ? <p className="rc-desc">{item.shortDescription}</p> : null}

        <div className="rc-meta">
          {item.repositoryCount !== null && item.repositoryCount !== undefined ? (
            <Meta label="repos">{formatCount(item.repositoryCount)}</Meta>
          ) : null}
          {item.released ? <Meta label="released">{item.released}</Meta> : null}
          {item.createdBy ? <Meta label="by">{item.createdBy}</Meta> : null}
          {item.updatedAt ? (
            <Meta>
              <RelTime iso={item.updatedAt} prefix="Updated" />
            </Meta>
          ) : null}
        </div>
      </div>
    </article>
  );
}
