import type { JSX } from "react";
import type { IssueItem } from "../../types/searchItems";
import { labelColors } from "../../lib/labelColor";
import { ActorChip, Avatar, Badge, ExternalLink, LanguageDot, Meta, RelTime } from "./primitives";
import { formatCount } from "./format";

const MAX_ASSIGNEE_AVATARS = 4;

export function IssueResultCard({ item }: { item: IssueItem }): JSX.Element {
  const isPr = item.isPullRequest === true;
  const closed = item.state?.toLowerCase() === "closed";
  const notPlanned = closed && item.stateReason === "not_planned";
  const labels = item.labels ?? [];
  const assignees = item.assignees ?? [];
  const repoName = item.repositoryFullName ?? joinOwnerRepo(item.owner, item.repo);
  // Only a GraphQL-backed search knows the repository's star count. A plain
  // REST issue search does not, and rendering "0 stars" there would be a lie —
  // so an unknown count renders nothing at all.
  const stars = knownCount(item.stars);

  return (
    <article className="rc-card">
      <div className="rc-head">
        <span
          className={`rc-state rc-state-${closed ? "closed" : "open"}`}
          title={notPlanned ? "Closed as not planned" : undefined}
        >
          {closed ? (notPlanned ? "closed · not planned" : "closed") : "open"}
        </span>
        <h3 className="rc-title">
          <ExternalLink href={item.url}>{item.title}</ExternalLink>
        </h3>
        <div className="rc-badges">
          <Badge tone={isPr ? "accent" : "neutral"}>{isPr ? "Pull request" : "Issue"}</Badge>
          {isPr && item.draft ? <Badge tone="draft">draft</Badge> : null}
          {item.milestone ? <Badge title="Milestone">{item.milestone}</Badge> : null}
        </div>
      </div>

      <p className="rc-subline">
        {repoName ? <span className="rc-repo-ref">{repoName}</span> : null}
        <span className="rc-number">#{item.number}</span>
      </p>

      {/* Freshness and popularity are the two signals this search is about, so
          they share one line instead of the star count being buried below. */}
      <div className="rc-signals">
        <p className="rc-fresh rc-fresh-strong">
          <span className="rc-fresh-dot" aria-hidden />
          <RelTime iso={item.createdAt} prefix="Opened" />
          {item.updatedAt ? (
            <span className="rc-fresh-secondary">
              <RelTime iso={item.updatedAt} prefix="· updated" />
            </span>
          ) : null}
        </p>

        {stars !== null ? (
          <span
            className="rc-stars"
            title={`${formatCount(stars)} ${stars === 1 ? "star" : "stars"} on ${
              repoName || "this repository"
            }`}
          >
            <span className="rc-star-glyph" aria-hidden="true">
              ★
            </span>
            <span className="rc-stars-count">{formatCount(stars)}</span>
            <span className="rc-stars-label">{stars === 1 ? "star" : "stars"}</span>
          </span>
        ) : null}
      </div>

      {labels.length > 0 ? (
        <ul className="rc-labels">
          {labels.map((label) => {
            const colors = labelColors(label.color);
            return (
              <li
                key={label.name}
                className="rc-label"
                style={{
                  background: colors.background,
                  color: colors.color,
                  borderColor: colors.border,
                }}
              >
                {label.name}
              </li>
            );
          })}
        </ul>
      ) : null}

      <div className="rc-meta">
        {/* Same treatment RepositoryCard gives a repo's language — it arrives
            with `stars`, from the same GraphQL search, and is absent otherwise. */}
        {item.language ? <LanguageDot language={item.language} /> : null}
        <Meta>
          <ActorChip actor={item.author} fallback="ghost" />
        </Meta>
        <Meta label="comments">{formatCount(item.comments, "0")}</Meta>
        <Meta label="reactions">{formatCount(item.reactions, "0")}</Meta>
        {item.closedAt ? (
          <Meta>
            <RelTime iso={item.closedAt} prefix="Closed" />
          </Meta>
        ) : null}
        {assignees.length > 0 ? (
          <span className="rc-meta-item">
            <span className="rc-meta-label">assigned</span>
            <span className="rc-avatar-stack">
              {assignees.slice(0, MAX_ASSIGNEE_AVATARS).map((assignee) => (
                <span key={assignee.login} className="rc-stack-item" title={assignee.login}>
                  <Avatar src={assignee.avatarUrl} alt={assignee.login} size={20} />
                </span>
              ))}
              {assignees.length > MAX_ASSIGNEE_AVATARS ? (
                <span
                  className="rc-stack-more"
                  title={assignees
                    .slice(MAX_ASSIGNEE_AVATARS)
                    .map((a) => a.login)
                    .join(", ")}
                >
                  +{assignees.length - MAX_ASSIGNEE_AVATARS}
                </span>
              ) : null}
            </span>
          </span>
        ) : (
          <span className="rc-meta-item rc-unassigned" title="Nobody is assigned to this yet">
            unassigned
          </span>
        )}
      </div>
    </article>
  );
}

function joinOwnerRepo(owner: string | null | undefined, repo: string | null | undefined): string {
  if (owner && repo) return `${owner}/${repo}`;
  return repo ?? owner ?? "";
}

/** A real count, or null for "the backend did not know" — 0 is a real count. */
function knownCount(n: number | null | undefined): number | null {
  return typeof n === "number" && Number.isFinite(n) ? n : null;
}
