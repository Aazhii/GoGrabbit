import type { JSX } from "react";
import type { RepositoryItem } from "../../types/searchItems";
import { Badge, ExternalLink, LanguageDot, Meta, RelTime } from "./primitives";
import { formatCount, formatSizeKb } from "./format";

const MAX_TOPICS = 8;

export function RepositoryCard({ item }: { item: RepositoryItem }): JSX.Element {
  const [owner, repoName] = splitFullName(item.fullName, item.name);
  const topics = item.topics ?? [];
  const shownTopics = topics.slice(0, MAX_TOPICS);
  const extraTopics = topics.length - shownTopics.length;
  const size = formatSizeKb(item.sizeKb);
  const nonPublic = item.visibility && item.visibility.toLowerCase() !== "public";

  return (
    <article className="rc-card">
      <div className="rc-head">
        <h3 className="rc-title">
          <ExternalLink href={item.url}>
            <span className="rc-owner">{owner}</span>
            <span className="rc-repo-name">{repoName}</span>
          </ExternalLink>
        </h3>
        <div className="rc-badges">
          {item.archived ? <Badge tone="warning">archived</Badge> : null}
          {item.isTemplate ? <Badge tone="accent">template</Badge> : null}
          {item.isFork ? <Badge>fork</Badge> : null}
          {nonPublic ? <Badge tone="closed">{item.visibility}</Badge> : null}
        </div>
      </div>

      {item.description ? <p className="rc-desc">{item.description}</p> : null}

      {shownTopics.length > 0 ? (
        <ul className="rc-topics">
          {shownTopics.map((topic) => (
            <li key={topic} className="rc-topic">
              {topic}
            </li>
          ))}
          {extraTopics > 0 ? (
            <li className="rc-topic rc-topic-more" title={topics.slice(MAX_TOPICS).join(", ")}>
              +{extraTopics}
            </li>
          ) : null}
        </ul>
      ) : null}

      <div className="rc-meta">
        {item.language ? <LanguageDot language={item.language} /> : null}
        <Meta label="★">{formatCount(item.stars, "0")}</Meta>
        <Meta label="forks">{formatCount(item.forks, "0")}</Meta>
        <Meta label="open issues">{formatCount(item.openIssues, "0")}</Meta>
        {item.license ? <Meta>{item.license}</Meta> : null}
        {size ? <Meta>{size}</Meta> : null}
        {item.homepage ? (
          <ExternalLink className="rc-meta-item rc-link" href={item.homepage}>
            homepage
          </ExternalLink>
        ) : null}
      </div>

      <div className="rc-meta rc-meta-time">
        {item.pushedAt ? (
          <span className="rc-fresh">
            <span className="rc-fresh-dot" aria-hidden />
            <RelTime iso={item.pushedAt} prefix="Pushed" />
          </span>
        ) : null}
        {item.updatedAt ? (
          <Meta>
            <RelTime iso={item.updatedAt} prefix="Updated" />
          </Meta>
        ) : null}
        {item.createdAt ? (
          <Meta>
            <RelTime iso={item.createdAt} prefix="Created" />
          </Meta>
        ) : null}
      </div>
    </article>
  );
}

function splitFullName(fullName: string, name: string): [string, string] {
  const idx = fullName.lastIndexOf("/");
  if (idx <= 0) return ["", fullName || name];
  return [fullName.slice(0, idx + 1), fullName.slice(idx + 1)];
}
