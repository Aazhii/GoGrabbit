package com.gograbbit.search;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.gograbbit.search.QualifierSpec.bool;
import static com.gograbbit.search.QualifierSpec.dateRange;
import static com.gograbbit.search.QualifierSpec.enumMulti;
import static com.gograbbit.search.QualifierSpec.enumOf;
import static com.gograbbit.search.QualifierSpec.flag;
import static com.gograbbit.search.QualifierSpec.freeText;
import static com.gograbbit.search.QualifierSpec.multiText;
import static com.gograbbit.search.QualifierSpec.numberRange;
import static com.gograbbit.search.QualifierSpec.text;

/**
 * The full, static description of all seven GitHub search endpoints: their sort
 * enums, their rate-limit buckets and ~116 qualifiers.
 *
 * <p>This exists so nothing downstream hand-writes one {@code @RequestParam} per
 * qualifier. The controller takes an opaque parameter map, {@link SearchQueryBuilder}
 * validates and renders it against the spec found here, and the frontend renders
 * its form from the same data via {@code GET /search/catalog}.
 *
 * <p>Facts encoded here that are easy to get wrong:
 * <ul>
 *   <li>Every endpoint has a DIFFERENT sort enum and an unknown value is a 422 from
 *       GitHub — so sorts are validated locally against these lists.</li>
 *   <li>Topics supports no sorting at all: empty {@code sorts}, {@code supportsOrder=false},
 *       and neither parameter is ever sent.</li>
 *   <li>Code search requires a token and lives in its own 10/min {@code code_search} bucket.</li>
 *   <li>Labels needs a numeric {@code repository_id} and its {@code q} accepts keywords ONLY
 *       — no qualifiers whatsoever.</li>
 *   <li>Boolean {@code AND}/{@code OR}/{@code NOT} work for issues only.</li>
 * </ul>
 */
@Component
public class SearchCatalog {

    private static final String G_TEXT = "text";
    private static final String G_SCOPE = "scope";
    private static final String G_PEOPLE = "people";
    private static final String G_METRICS = "metrics";
    private static final String G_DATES = "dates";
    private static final String G_ATTRIBUTES = "attributes";
    private static final String G_STATE = "state";

    private static final GroupSpec GROUP_TEXT = new GroupSpec(G_TEXT, "Text");
    private static final GroupSpec GROUP_SCOPE = new GroupSpec(G_SCOPE, "Scope");
    private static final GroupSpec GROUP_PEOPLE = new GroupSpec(G_PEOPLE, "People");
    private static final GroupSpec GROUP_METRICS = new GroupSpec(G_METRICS, "Numbers & activity");
    private static final GroupSpec GROUP_DATES = new GroupSpec(G_DATES, "Dates");
    private static final GroupSpec GROUP_ATTRIBUTES = new GroupSpec(G_ATTRIBUTES, "Attributes");
    private static final GroupSpec GROUP_STATE = new GroupSpec(G_STATE, "State");

    public static final String BUCKET_SEARCH = "search";
    public static final String BUCKET_CODE_SEARCH = "code_search";

    private final Map<SearchType, SearchTypeSpec> specs;
    private final List<SearchTypeSpec> ordered;

    public SearchCatalog() {
        Map<SearchType, SearchTypeSpec> built = new EnumMap<>(SearchType.class);
        built.put(SearchType.REPOSITORIES, repositories());
        built.put(SearchType.ISSUES, issues());
        built.put(SearchType.USERS, users());
        built.put(SearchType.CODE, code());
        built.put(SearchType.COMMITS, commits());
        built.put(SearchType.TOPICS, topics());
        built.put(SearchType.LABELS, labels());
        this.specs = Map.copyOf(built);

        List<SearchTypeSpec> list = new ArrayList<>();
        for (SearchType type : SearchType.values()) {
            list.add(built.get(type));
        }
        this.ordered = List.copyOf(list);
    }

    /** All seven specs, in {@link SearchType} declaration order. */
    public List<SearchTypeSpec> all() {
        return ordered;
    }

    public SearchTypeSpec spec(SearchType type) {
        return specs.get(type);
    }

    // ---------------------------------------------------------------- repositories

    private static SearchTypeSpec repositories() {
        List<QualifierSpec> q = List.of(
                freeText(G_TEXT, "Free-text terms matched against the repository name, description and README."),
                enumMulti("in", "in", "Search in", "Restrict the free-text match to these fields.",
                        G_TEXT, List.of("name", "description", "readme", "topics")),

                text("repo", "repo", "Repository", "A single repository, as owner/repo.", G_SCOPE, "spotify/backstage"),
                text("user", "user", "Owned by user", "Repositories owned by this user.", G_SCOPE, "torvalds"),
                text("org", "org", "Owned by org", "Repositories owned by this organization.", G_SCOPE, "github"),

                numberRange("size", "size", "Size", "Repository size.", G_METRICS, "KB", ">=1000"),
                numberRange("followers", "followers", "Followers", "Number of followers.", G_METRICS, null, ">=100"),
                numberRange("forks", "forks", "Forks", "Number of forks.", G_METRICS, null, "10..100"),
                numberRange("stars", "stars", "Stars", "Number of stars.", G_METRICS, null, ">=500"),
                numberRange("topics", "topics", "Topic count", "How MANY topics the repository has "
                        + "(use 'topic' to match a topic by name).", G_METRICS, null, ">=3"),
                numberRange("goodFirstIssues", "good-first-issues", "Good first issues",
                        "Number of open issues labelled 'good first issue'.", G_METRICS, null, ">=5"),
                numberRange("helpWantedIssues", "help-wanted-issues", "Help-wanted issues",
                        "Number of open issues labelled 'help wanted'.", G_METRICS, null, ">=5"),

                dateRange("created", "created", "Created", "When the repository was created.", G_DATES),
                dateRange("pushed", "pushed", "Last pushed", "When the repository was last pushed to.", G_DATES),

                text("language", "language", "Language", "Primary language.", G_ATTRIBUTES, "java"),
                text("topic", "topic", "Topic name", "Match a topic by NAME (use 'topics' for a topic count).",
                        G_ATTRIBUTES, "machine-learning"),
                text("license", "license", "License", "SPDX-ish license keyword.", G_ATTRIBUTES, "apache-2.0"),

                flag("isPublic", "is", "public", "Public", "Only public repositories.", G_ATTRIBUTES),
                flag("isPrivate", "is", "private", "Private", "Only private repositories (needs a token with access).",
                        G_ATTRIBUTES),
                flag("isSponsorable", "is", "sponsorable", "Sponsorable",
                        "Repositories whose owner has GitHub Sponsors enabled.", G_ATTRIBUTES),
                flag("hasFundingFile", "has", "funding-file", "Has funding file",
                        "Repositories with a FUNDING.yml.", G_ATTRIBUTES),

                bool("mirror", "mirror", "Mirror", "Whether the repository is a mirror.", G_ATTRIBUTES),
                bool("template", "template", "Template", "Whether the repository is a template.", G_ATTRIBUTES),
                bool("archived", "archived", "Archived", "Whether the repository is archived.", G_ATTRIBUTES),
                enumOf("fork", "fork", "Include forks",
                        "'true' includes forks alongside sources; 'only' returns forks exclusively.",
                        G_ATTRIBUTES, List.of("true", "only"))
        );

        return new SearchTypeSpec(
                SearchType.REPOSITORIES, SearchType.REPOSITORIES.slug(), SearchType.REPOSITORIES.path(),
                "Repositories",
                List.of(SortSpec.bestMatch(),
                        new SortSpec("stars", "Stars"),
                        new SortSpec("forks", "Forks"),
                        new SortSpec("help-wanted-issues", "Help-wanted issues"),
                        new SortSpec("updated", "Recently updated")),
                true, false, BUCKET_SEARCH, false, false, false,
                List.of(GROUP_TEXT, GROUP_SCOPE, GROUP_METRICS, GROUP_DATES, GROUP_ATTRIBUTES),
                q);
    }

    // ---------------------------------------------------------------- issues

    private static SearchTypeSpec issues() {
        List<QualifierSpec> q = List.of(
                freeText(G_TEXT, "Free-text terms. Issues search also supports AND / OR / NOT and "
                        + "parentheses (max 5 levels deep)."),
                enumMulti("in", "in", "Search in", "Restrict the free-text match to these fields.",
                        G_TEXT, List.of("title", "body", "comments")),

                enumOf("type", "type", "Type", "Issues or pull requests.", G_STATE, List.of("issue", "pr")),
                enumOf("state", "state", "State", "Open or closed.", G_STATE, List.of("open", "closed")),
                flag("isMerged", "is", "merged", "Merged", "Merged pull requests only.", G_STATE),
                flag("isUnmerged", "is", "unmerged", "Unmerged", "Closed-but-unmerged pull requests only.", G_STATE),
                flag("isQueued", "is", "queued", "Queued", "Pull requests in a merge queue.", G_STATE),
                flag("isLocked", "is", "locked", "Locked", "Conversation locked.", G_STATE),
                flag("isUnlocked", "is", "unlocked", "Unlocked", "Conversation not locked.", G_STATE),
                flag("isPublic", "is", "public", "In public repos", "Only items in public repositories.", G_STATE),
                flag("isPrivate", "is", "private", "In private repos", "Only items in private repositories.", G_STATE),
                bool("draft", "draft", "Draft", "Whether the pull request is a draft.", G_STATE),
                enumOf("reason", "reason", "Close reason", "Why a closed issue was closed.",
                        G_STATE, List.of("completed", "not planned", "reopened")),

                text("repo", "repo", "Repository", "A single repository, as owner/repo.", G_SCOPE, "spotify/backstage"),
                text("user", "user", "Owned by user", "Items in repositories owned by this user.", G_SCOPE, "torvalds"),
                text("org", "org", "Owned by org", "Items in repositories owned by this organization.", G_SCOPE, "github"),

                text("author", "author", "Author", "Who opened it.", G_PEOPLE, "octocat"),
                text("assignee", "assignee", "Assignee", "Who it is assigned to; '*' means anyone.", G_PEOPLE, "octocat"),
                text("mentions", "mentions", "Mentions", "Who is @-mentioned.", G_PEOPLE, "octocat"),
                text("team", "team", "Team", "A team that is mentioned, as org/team.", G_PEOPLE, "github/docs"),
                text("commenter", "commenter", "Commenter", "Who commented.", G_PEOPLE, "octocat"),
                text("involves", "involves", "Involves", "Author, assignee, mentioned or commenter.", G_PEOPLE, "octocat"),
                text("reviewedBy", "reviewed-by", "Reviewed by", "Who reviewed the pull request.", G_PEOPLE, "octocat"),
                text("reviewRequested", "review-requested", "Review requested from",
                        "Whose review was requested.", G_PEOPLE, "octocat"),

                multiText("label", "label", "Labels",
                        "Comma-joined into ONE label: qualifier, which is OR. Repeating the qualifier would AND them.",
                        G_ATTRIBUTES, "bug,good first issue"),
                text("milestone", "milestone", "Milestone", "Milestone title.", G_ATTRIBUTES, "v2.0"),
                text("project", "project", "Project board", "Project board number, as owner/repo/number.",
                        G_ATTRIBUTES, "github/docs/1"),
                enumMulti("no", "no", "Missing", "Items with NO value for these fields. Repeatable; each "
                                + "occurrence is a separate no: qualifier (AND).",
                        G_ATTRIBUTES, List.of("label", "milestone", "assignee", "project")),
                enumOf("linked", "linked", "Linked to", "Issues with a linked pull request, or vice versa.",
                        G_ATTRIBUTES, List.of("pr", "issue")),
                text("head", "head", "Head branch", "Pull request source branch.", G_ATTRIBUTES, "feature/x"),
                text("base", "base", "Base branch", "Pull request target branch.", G_ATTRIBUTES, "main"),
                enumOf("status", "status", "Commit status", "Latest commit status on a pull request.",
                        G_ATTRIBUTES, List.of("pending", "success", "failure")),
                enumOf("review", "review", "Review state", "Pull request review state.",
                        G_ATTRIBUTES, List.of("none", "required", "approved", "changes_requested")),
                text("language", "language", "Language", "Primary language of the repository.", G_ATTRIBUTES, "java"),
                bool("archived", "archived", "In archived repos", "Whether the repository is archived.", G_ATTRIBUTES),

                numberRange("comments", "comments", "Comments", "Number of comments.", G_METRICS, null, ">10"),
                numberRange("interactions", "interactions", "Interactions", "Reactions plus comments.",
                        G_METRICS, null, ">50"),
                numberRange("reactions", "reactions", "Reactions", "Number of reactions.", G_METRICS, null, ">5"),

                dateRange("created", "created", "Created", "When it was opened.", G_DATES),
                dateRange("updated", "updated", "Updated", "When it was last updated.", G_DATES),
                dateRange("closed", "closed", "Closed", "When it was closed.", G_DATES),
                dateRange("merged", "merged", "Merged", "When the pull request was merged.", G_DATES)
        );

        return new SearchTypeSpec(
                SearchType.ISSUES, SearchType.ISSUES.slug(), SearchType.ISSUES.path(),
                "Issues & pull requests",
                List.of(SortSpec.bestMatch(),
                        new SortSpec("comments", "Comments"),
                        new SortSpec("reactions", "Reactions"),
                        new SortSpec("reactions-+1", "Reactions: +1"),
                        new SortSpec("reactions--1", "Reactions: -1"),
                        new SortSpec("reactions-smile", "Reactions: smile"),
                        new SortSpec("reactions-thinking_face", "Reactions: thinking face"),
                        new SortSpec("reactions-heart", "Reactions: heart"),
                        new SortSpec("reactions-tada", "Reactions: tada"),
                        new SortSpec("interactions", "Interactions"),
                        new SortSpec("created", "Created"),
                        new SortSpec("updated", "Updated")),
                true, false, BUCKET_SEARCH, false, false, true,
                List.of(GROUP_TEXT, GROUP_STATE, GROUP_SCOPE, GROUP_PEOPLE, GROUP_ATTRIBUTES, GROUP_METRICS, GROUP_DATES),
                q);
    }

    // ---------------------------------------------------------------- users

    private static SearchTypeSpec users() {
        List<QualifierSpec> q = List.of(
                freeText(G_TEXT, "Free-text terms matched against login, name and public email."),
                enumMulti("in", "in", "Search in", "Restrict the free-text match to these fields.",
                        G_TEXT, List.of("login", "name", "email")),
                enumOf("type", "type", "Account type", "Personal accounts or organizations.",
                        G_ATTRIBUTES, List.of("user", "org")),

                text("user", "user", "Login", "An exact account login.", G_SCOPE, "octocat"),
                text("org", "org", "Organization", "Members of this organization.", G_SCOPE, "github"),

                text("fullname", "fullname", "Full name", "Match the profile's full name.", G_ATTRIBUTES, "Jane Doe"),
                text("location", "location", "Location", "Profile location.", G_ATTRIBUTES, "Berlin"),
                text("language", "language", "Language", "Language used in the account's repositories.",
                        G_ATTRIBUTES, "rust"),
                flag("isSponsorable", "is", "sponsorable", "Sponsorable", "Has GitHub Sponsors enabled.", G_ATTRIBUTES),

                numberRange("repos", "repos", "Public repos", "Number of public repositories.", G_METRICS, null, ">10"),
                numberRange("followers", "followers", "Followers", "Number of followers.", G_METRICS, null, ">=1000"),

                dateRange("created", "created", "Joined", "When the account was created.", G_DATES)
        );

        return new SearchTypeSpec(
                SearchType.USERS, SearchType.USERS.slug(), SearchType.USERS.path(),
                "Users & organizations",
                List.of(SortSpec.bestMatch(),
                        new SortSpec("followers", "Followers"),
                        new SortSpec("repositories", "Repositories"),
                        new SortSpec("joined", "Joined")),
                true, false, BUCKET_SEARCH, false, false, false,
                List.of(GROUP_TEXT, GROUP_SCOPE, GROUP_ATTRIBUTES, GROUP_METRICS, GROUP_DATES),
                q);
    }

    // ---------------------------------------------------------------- code

    private static SearchTypeSpec code() {
        List<QualifierSpec> q = List.of(
                freeText(G_TEXT, "Required. Code search needs at least one search term; it uses GitHub's "
                        + "LEGACY code-search syntax, only indexes default branches and skips files over 384 KB."),
                enumMulti("in", "in", "Search in", "Match the term in file contents and/or the file path.",
                        G_TEXT, List.of("file", "path")),

                text("user", "user", "Owned by user", "Code in repositories owned by this user.", G_SCOPE, "torvalds"),
                text("org", "org", "Owned by org", "Code in repositories owned by this organization.", G_SCOPE, "github"),
                text("repo", "repo", "Repository", "A single repository, as owner/repo.", G_SCOPE, "spotify/backstage"),

                text("path", "path", "Path", "Directory the file lives in.", G_ATTRIBUTES, "src/main"),
                text("language", "language", "Language", "File language.", G_ATTRIBUTES, "java"),
                text("filename", "filename", "Filename", "Exact file name.", G_ATTRIBUTES, "pom.xml"),
                text("extension", "extension", "Extension", "File extension, without the dot.", G_ATTRIBUTES, "java"),
                enumOf("fork", "fork", "Include forks", "'true' includes forks; 'only' returns forks exclusively.",
                        G_ATTRIBUTES, List.of("true", "only")),

                numberRange("size", "size", "File size", "File size.", G_METRICS, "bytes", ">1000")
        );

        return new SearchTypeSpec(
                SearchType.CODE, SearchType.CODE.slug(), SearchType.CODE.path(),
                "Code",
                List.of(SortSpec.bestMatch(), new SortSpec("indexed", "Recently indexed")),
                true, true, BUCKET_CODE_SEARCH, false, true, false,
                List.of(GROUP_TEXT, GROUP_SCOPE, GROUP_ATTRIBUTES, GROUP_METRICS),
                q);
    }

    // ---------------------------------------------------------------- commits

    private static SearchTypeSpec commits() {
        List<QualifierSpec> q = List.of(
                freeText(G_TEXT, "Free-text terms matched against the commit message. Only default branches "
                        + "are indexed."),

                text("author", "author", "Author (login)", "GitHub login of the author.", G_PEOPLE, "octocat"),
                text("committer", "committer", "Committer (login)", "GitHub login of the committer.", G_PEOPLE, "octocat"),
                text("authorName", "author-name", "Author name", "Name recorded in the commit.", G_PEOPLE, "Jane Doe"),
                text("committerName", "committer-name", "Committer name", "Name recorded in the commit.",
                        G_PEOPLE, "Jane Doe"),
                text("authorEmail", "author-email", "Author email", "Email recorded in the commit.",
                        G_PEOPLE, "jane@example.com"),
                text("committerEmail", "committer-email", "Committer email", "Email recorded in the commit.",
                        G_PEOPLE, "jane@example.com"),

                text("user", "user", "Owned by user", "Commits in repositories owned by this user.", G_SCOPE, "torvalds"),
                text("org", "org", "Owned by org", "Commits in repositories owned by this organization.",
                        G_SCOPE, "github"),
                text("repo", "repo", "Repository", "A single repository, as owner/repo.", G_SCOPE, "torvalds/linux"),

                bool("merge", "merge", "Merge commits", "Whether the commit is a merge commit.", G_ATTRIBUTES),
                text("hash", "hash", "Commit SHA", "Full or partial commit SHA.", G_ATTRIBUTES, "8a1e2f0"),
                text("parent", "parent", "Parent SHA", "Commits whose parent has this SHA.", G_ATTRIBUTES, "8a1e2f0"),
                text("tree", "tree", "Tree SHA", "Commits with this tree SHA.", G_ATTRIBUTES, "8a1e2f0"),
                flag("isPublic", "is", "public", "In public repos", "Only commits in public repositories.", G_ATTRIBUTES),
                flag("isPrivate", "is", "private", "In private repos", "Only commits in private repositories.",
                        G_ATTRIBUTES),

                dateRange("authorDate", "author-date", "Author date", "When the change was authored.", G_DATES),
                dateRange("committerDate", "committer-date", "Committer date", "When the change was committed.", G_DATES)
        );

        return new SearchTypeSpec(
                SearchType.COMMITS, SearchType.COMMITS.slug(), SearchType.COMMITS.path(),
                "Commits",
                List.of(SortSpec.bestMatch(),
                        new SortSpec("author-date", "Author date"),
                        new SortSpec("committer-date", "Committer date")),
                true, false, BUCKET_SEARCH, false, false, false,
                List.of(GROUP_TEXT, GROUP_PEOPLE, GROUP_SCOPE, GROUP_ATTRIBUTES, GROUP_DATES),
                q);
    }

    // ---------------------------------------------------------------- topics

    private static SearchTypeSpec topics() {
        List<QualifierSpec> q = List.of(
                freeText(G_TEXT, "Free-text terms matched against the topic name and description."),

                flag("isCurated", "is", "curated", "Curated", "Topics curated by the community.", G_ATTRIBUTES),
                flag("isNotCurated", "is", "not-curated", "Not curated", "Topics without curation.", G_ATTRIBUTES),
                flag("isFeatured", "is", "featured", "Featured", "Topics featured on github.com/topics.", G_ATTRIBUTES),
                flag("isNotFeatured", "is", "not-featured", "Not featured", "Topics that are not featured.",
                        G_ATTRIBUTES),

                numberRange("repositories", "repositories", "Repositories",
                        "How many repositories use the topic.", G_METRICS, null, ">=1000"),
                dateRange("created", "created", "Created", "When the topic was created.", G_DATES)
        );

        // Topics is the one endpoint with no sort support at all: sending sort or
        // order is meaningless, so the catalog advertises an empty sort list and
        // SearchService refuses either parameter rather than forwarding it.
        return new SearchTypeSpec(
                SearchType.TOPICS, SearchType.TOPICS.slug(), SearchType.TOPICS.path(),
                "Topics",
                List.of(),
                false, false, BUCKET_SEARCH, false, false, false,
                List.of(GROUP_TEXT, GROUP_ATTRIBUTES, GROUP_METRICS, GROUP_DATES),
                q);
    }

    // ---------------------------------------------------------------- labels

    private static SearchTypeSpec labels() {
        // GitHub's label search takes keywords only — NO qualifiers of any kind —
        // plus a mandatory numeric repository_id. Hence exactly one qualifier here.
        List<QualifierSpec> q = List.of(
                freeText(G_TEXT, "Keywords only. GitHub's label search rejects qualifiers; scope it with "
                        + "repositoryId (or owner + repo, which this API resolves for you).")
        );

        return new SearchTypeSpec(
                SearchType.LABELS, SearchType.LABELS.slug(), SearchType.LABELS.path(),
                "Labels",
                List.of(SortSpec.bestMatch(),
                        new SortSpec("created", "Created"),
                        new SortSpec("updated", "Updated")),
                true, false, BUCKET_SEARCH, true, false, false,
                List.of(GROUP_TEXT),
                q);
    }
}
