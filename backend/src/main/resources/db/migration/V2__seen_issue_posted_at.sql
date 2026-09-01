alter table seen_issue
    add column posted_at timestamptz not null default now();

create index seen_issue_posted_at_idx on seen_issue (posted_at);
