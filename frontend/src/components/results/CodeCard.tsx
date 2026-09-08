import type { JSX } from "react";
import type { CodeItem } from "../../types/searchItems";
import { Avatar, ExternalLink, LanguageDot, Meta } from "./primitives";
import { formatBytes } from "./format";

export function CodeCard({ item }: { item: CodeItem }): JSX.Element {
  const segments = (item.path ?? item.name).split("/").filter(Boolean);
  const fileName = segments.length > 0 ? segments[segments.length - 1] : item.name;
  const dirs = segments.slice(0, -1);
  const size = formatBytes(item.fileSize);
  const repo = item.repository;

  return (
    <article className="rc-card">
      <h3 className="rc-title rc-title-path">
        <ExternalLink href={item.url} className="rc-path" title={item.path}>
          {dirs.map((segment, i) => (
            <span key={`${segment}-${i}`} className="rc-path-seg">
              {segment}
              <span className="rc-path-sep" aria-hidden>
                /
              </span>
            </span>
          ))}
          <span className="rc-path-file">{fileName}</span>
        </ExternalLink>
      </h3>

      {repo ? (
        <p className="rc-subline rc-subline-flex">
          <Avatar src={repo.avatarUrl} alt={repo.owner ?? repo.fullName} size={18} />
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
        {item.language ? <LanguageDot language={item.language} /> : null}
        {size ? <Meta>{size}</Meta> : null}
        {item.sha ? (
          <Meta label="sha">
            <code className="rc-sha">{item.sha.slice(0, 7)}</code>
          </Meta>
        ) : null}
      </div>
    </article>
  );
}
