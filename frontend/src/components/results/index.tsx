import type { JSX } from "react";
import type {
  CodeItem,
  CommitItem,
  IssueItem,
  LabelItem,
  RepositoryItem,
  SearchItem,
  SearchTypeSlug,
  TopicItem,
  UserItem,
} from "../../types/searchItems";
import { RepositoryCard } from "./RepositoryCard";
import { IssueResultCard } from "./IssueResultCard";
import { UserCard } from "./UserCard";
import { CodeCard } from "./CodeCard";
import { CommitCard } from "./CommitCard";
import { TopicCard } from "./TopicCard";
import { LabelCard } from "./LabelCard";
import "./results.css";

export { RepositoryCard } from "./RepositoryCard";
export { IssueResultCard } from "./IssueResultCard";
export { UserCard } from "./UserCard";
export { CodeCard } from "./CodeCard";
export { CommitCard } from "./CommitCard";
export { TopicCard } from "./TopicCard";
export { LabelCard } from "./LabelCard";

/**
 * Renders one search result. The shell knows which search type produced the
 * item, so `type` is the discriminator — the item payloads themselves have no
 * common tag field.
 */
export function ResultCard({
  type,
  item,
}: {
  type: SearchTypeSlug;
  item: SearchItem;
}): JSX.Element {
  switch (type) {
    case "repositories":
      return <RepositoryCard item={item as RepositoryItem} />;
    case "issues":
      return <IssueResultCard item={item as IssueItem} />;
    case "users":
      return <UserCard item={item as UserItem} />;
    case "code":
      return <CodeCard item={item as CodeItem} />;
    case "commits":
      return <CommitCard item={item as CommitItem} />;
    case "topics":
      return <TopicCard item={item as TopicItem} />;
    case "labels":
      return <LabelCard item={item as LabelItem} />;
    default:
      return <UnknownCard type={type} />;
  }
}

function UnknownCard({ type }: { type: never | string }): JSX.Element {
  return (
    <article className="rc-card">
      <p className="rc-desc">No card is defined for result type “{String(type)}”.</p>
    </article>
  );
}
