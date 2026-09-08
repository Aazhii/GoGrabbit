import type { JSX } from "react";
import type { LabelItem } from "../../types/searchItems";
import { labelColors } from "../../lib/labelColor";
import { Badge, ExternalLink } from "./primitives";

export function LabelCard({ item }: { item: LabelItem }): JSX.Element {
  const colors = labelColors(item.color);

  return (
    <article className="rc-card">
      <div className="rc-head">
        <h3 className="rc-title rc-title-plain">
          <ExternalLink href={item.url}>
            <span
              className="rc-label rc-label-lg"
              style={{
                background: colors.background,
                color: colors.color,
                borderColor: colors.border,
              }}
            >
              {item.name}
            </span>
          </ExternalLink>
        </h3>
        <div className="rc-badges">
          {item.isDefault ? <Badge tone="accent">default</Badge> : null}
          {item.color ? <code className="rc-sha">#{item.color.replace(/^#/, "")}</code> : null}
        </div>
      </div>

      {item.description ? <p className="rc-desc">{item.description}</p> : null}
    </article>
  );
}
