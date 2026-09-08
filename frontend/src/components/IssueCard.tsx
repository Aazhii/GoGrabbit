import { labelColors } from "../lib/labelColor";
import { formatAbsolute, formatRelative } from "../lib/relativeTime";
import type { IssueSearchResult } from "../types";

interface IssueCardProps {
  issue: IssueSearchResult;
}

export default function IssueCard({ issue }: IssueCardProps) {
  const created = formatRelative(issue.createdAt);
  const updated = formatRelative(issue.updatedAt);

  return (
    <li className="issue-card">
      <div className="issue-card-head">
        <span className={`state-pill state-${issue.state}`}>{issue.state}</span>
        <a className="issue-title" href={issue.url} target="_blank" rel="noreferrer">
          {issue.title}
        </a>
      </div>

      <p className="issue-freshness">
        <strong title={formatAbsolute(issue.createdAt)}>Created {created || "at an unknown time"}</strong>
        {updated && (
          <span className="issue-updated" title={formatAbsolute(issue.updatedAt)}>
            {" · updated "}
            {updated}
          </span>
        )}
      </p>

      <p className="issue-repo">
        <a href={`https://github.com/${issue.owner}/${issue.repo}`} target="_blank" rel="noreferrer">
          {issue.repositoryFullName || `${issue.owner}/${issue.repo}`}
        </a>
        <span className="issue-number">#{issue.number}</span>
      </p>

      {issue.labels.length > 0 && (
        <ul className="issue-labels">
          {issue.labels.map((label) => {
            const colors = labelColors(label.color);
            return (
              <li
                key={label.name}
                className="issue-label"
                style={{
                  backgroundColor: colors.background,
                  color: colors.color,
                  borderColor: colors.border,
                }}
              >
                {label.name}
              </li>
            );
          })}
        </ul>
      )}

      <div className="issue-footer">
        {issue.author ? (
          <a className="user-chip" href={issue.author.url} target="_blank" rel="noreferrer">
            {issue.author.avatarUrl && (
              <img src={issue.author.avatarUrl} alt="" width={20} height={20} loading="lazy" />
            )}
            {issue.author.login}
          </a>
        ) : (
          <span className="user-chip user-chip-unknown">unknown author</span>
        )}

        <span className="issue-comments" title={`${issue.comments} comment(s)`}>
          💬 {issue.comments}
        </span>

        {issue.assignees.length > 0 && (
          <span className="issue-assignees">
            <span className="assignees-label">Assigned:</span>
            {issue.assignees.map((assignee) => (
              <a
                key={assignee.login}
                className="user-chip"
                href={assignee.url}
                target="_blank"
                rel="noreferrer"
              >
                {assignee.avatarUrl && (
                  <img src={assignee.avatarUrl} alt="" width={20} height={20} loading="lazy" />
                )}
                {assignee.login}
              </a>
            ))}
          </span>
        )}
      </div>
    </li>
  );
}
