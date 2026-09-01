create table watched_repo (
    id               uuid primary key default gen_random_uuid(),
    owner            text not null,
    repo             text not null,
    labels           text[] not null,
    interval_minutes int not null default 30,
    active           boolean not null default true,
    created_at       timestamptz not null default now(),
    unique (owner, repo)
);

create table seen_issue (
    id              uuid primary key default gen_random_uuid(),
    github_issue_id bigint not null,
    watched_repo_id uuid not null references watched_repo(id) on delete cascade,
    title           text not null,
    url             text not null,
    labeled_at      timestamptz not null,
    notified_at     timestamptz not null default now(),
    unique (github_issue_id, watched_repo_id)
);

create table notification_channel (
    id      uuid primary key default gen_random_uuid(),
    type    text not null check (type in ('EMAIL', 'TELEGRAM', 'DISCORD')),
    target  text not null,
    active  boolean not null default true
);

create index seen_issue_watched_repo_id_idx on seen_issue (watched_repo_id);
