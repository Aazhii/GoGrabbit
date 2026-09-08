import type { JSX } from "react";
import type { UserItem } from "../../types/searchItems";
import { Avatar, Badge, ExternalLink, Meta, RelTime } from "./primitives";
import { formatCount } from "./format";

export function UserCard({ item }: { item: UserItem }): JSX.Element {
  const isOrg = (item.type ?? "").toLowerCase() === "organization";
  const blogHref = normalizeUrl(item.blog);

  return (
    <article className="rc-card rc-card-user">
      <div className="rc-user-avatar">
        <Avatar src={item.avatarUrl} alt={item.login} size={52} />
      </div>
      <div className="rc-user-body">
        <div className="rc-head">
          <h3 className="rc-title">
            <ExternalLink href={item.url}>{item.login}</ExternalLink>
          </h3>
          {item.name ? <span className="rc-user-name">{item.name}</span> : null}
          <div className="rc-badges">
            <Badge tone={isOrg ? "accent" : "neutral"}>{isOrg ? "Organization" : "User"}</Badge>
            {item.hireable ? <Badge tone="open">hireable</Badge> : null}
          </div>
        </div>

        {item.bio ? <p className="rc-desc">{item.bio}</p> : null}

        <div className="rc-meta">
          {item.location ? <Meta label="location">{item.location}</Meta> : null}
          {item.company ? <Meta label="company">{item.company}</Meta> : null}
          {item.blog && blogHref ? (
            <ExternalLink className="rc-meta-item rc-link rc-truncate" href={blogHref} title={item.blog}>
              {item.blog}
            </ExternalLink>
          ) : null}
          {item.email ? (
            <a className="rc-meta-item rc-link" href={`mailto:${item.email}`}>
              {item.email}
            </a>
          ) : null}
        </div>

        <div className="rc-meta rc-meta-time">
          <Meta label="followers">{formatCount(item.followers, "0")}</Meta>
          <Meta label="following">{formatCount(item.following, "0")}</Meta>
          <Meta label="repos">{formatCount(item.publicRepos, "0")}</Meta>
          {item.publicGists ? <Meta label="gists">{formatCount(item.publicGists)}</Meta> : null}
          {item.createdAt ? (
            <Meta>
              <RelTime iso={item.createdAt} prefix="Joined" />
            </Meta>
          ) : null}
        </div>
      </div>
    </article>
  );
}

function normalizeUrl(raw: string | null | undefined): string | null {
  const value = raw?.trim();
  if (!value) return null;
  if (/^https?:\/\//i.test(value)) return value;
  if (/^[\w.-]+\.[a-z]{2,}(\/|$)/i.test(value)) return `https://${value}`;
  return null;
}
