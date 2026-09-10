package com.gograbbit.search;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flattens GitHub's seven very different item shapes into the normalized field
 * names of the frozen wire contract.
 *
 * <p>Items are emitted as ordered maps rather than seven more records: every field
 * is a straight rename or a one-level unwrap, the shapes are frozen by the API
 * contract, and a map keeps missing fields as explicit nulls without a record per
 * type. Anything GitHub omits stays null — notably {@code /search/users} returns a
 * MINIMAL user object, so profile fields like {@code bio} and {@code publicRepos}
 * are null there unless GitHub decides to include them.
 */
@Component
public class SearchResultMapper {

    public List<Map<String, Object>> map(SearchType type, JsonNode items) {
        List<Map<String, Object>> mapped = new ArrayList<>();
        if (items == null || !items.isArray()) {
            return mapped;
        }
        for (JsonNode item : items) {
            mapped.add(switch (type) {
                case REPOSITORIES -> repository(item);
                case ISSUES -> issue(item);
                case USERS -> user(item);
                case CODE -> code(item);
                case COMMITS -> commit(item);
                case TOPICS -> topic(item);
                case LABELS -> label(item);
            });
        }
        return mapped;
    }

    // ---------------------------------------------------------------- per type

    private static Map<String, Object> repository(JsonNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", num(n, "id"));
        m.put("fullName", str(n, "full_name"));
        m.put("name", str(n, "name"));
        m.put("owner", actor(n.get("owner")));
        m.put("description", str(n, "description"));
        m.put("url", str(n, "html_url"));
        m.put("stars", num(n, "stargazers_count"));
        m.put("forks", num(n, "forks_count"));
        m.put("watchers", num(n, "watchers_count"));
        m.put("openIssues", num(n, "open_issues_count"));
        m.put("language", str(n, "language"));
        m.put("topics", strings(n.get("topics")));
        m.put("license", licenseName(n.get("license")));
        m.put("sizeKb", num(n, "size"));
        m.put("createdAt", str(n, "created_at"));
        m.put("updatedAt", str(n, "updated_at"));
        m.put("pushedAt", str(n, "pushed_at"));
        m.put("archived", bool(n, "archived"));
        m.put("isTemplate", bool(n, "is_template"));
        m.put("isFork", bool(n, "fork"));
        m.put("visibility", str(n, "visibility"));
        m.put("homepage", str(n, "homepage"));
        m.put("defaultBranch", str(n, "default_branch"));
        return m;
    }

    private static Map<String, Object> issue(JsonNode n) {
        String[] ownerRepo = ownerAndRepo(text(n, "repository_url"));

        List<Map<String, Object>> labels = new ArrayList<>();
        if (n.get("labels") != null && n.get("labels").isArray()) {
            for (JsonNode l : n.get("labels")) {
                Map<String, Object> label = new LinkedHashMap<>();
                label.put("name", str(l, "name"));
                label.put("color", str(l, "color"));
                labels.add(label);
            }
        }

        List<Map<String, Object>> assignees = new ArrayList<>();
        if (n.get("assignees") != null && n.get("assignees").isArray()) {
            for (JsonNode a : n.get("assignees")) {
                assignees.add(actor(a));
            }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", num(n, "id"));
        m.put("number", num(n, "number"));
        m.put("title", str(n, "title"));
        m.put("url", str(n, "html_url"));
        m.put("state", str(n, "state"));
        m.put("stateReason", str(n, "state_reason"));
        m.put("owner", ownerRepo == null ? null : ownerRepo[0]);
        m.put("repo", ownerRepo == null ? null : ownerRepo[1]);
        m.put("repositoryFullName", ownerRepo == null ? null : ownerRepo[0] + "/" + ownerRepo[1]);
        m.put("labels", labels);
        m.put("createdAt", str(n, "created_at"));
        m.put("updatedAt", str(n, "updated_at"));
        m.put("closedAt", str(n, "closed_at"));
        m.put("comments", num(n, "comments"));
        JsonNode reactions = n.get("reactions");
        m.put("reactions", reactions == null ? null : num(reactions, "total_count"));
        m.put("author", actor(n.get("user")));
        m.put("assignees", assignees);
        // Every pull request is also an issue in the search index; a non-null
        // `pull_request` object is the only reliable discriminator.
        m.put("isPullRequest", isPullRequest(n));
        m.put("draft", bool(n, "draft"));
        JsonNode milestone = n.get("milestone");
        m.put("milestone", milestone == null || milestone.isNull() ? null : str(milestone, "title"));
        // REST issue search carries no repository object, so there is nothing to
        // report here. The keys are still emitted so both paths have one shape and
        // the client can treat "no star data" as null rather than as absent.
        m.put("stars", null);
        m.put("language", null);
        return m;
    }

    /**
     * Maps GraphQL {@code search(type: ISSUE)} nodes onto exactly the same normalized
     * shape as {@link #issue}, so the client cannot tell which path served it — with
     * the one difference that {@code stars} and {@code language} are populated, which
     * is the entire reason the GraphQL path exists.
     */
    public List<Map<String, Object>> mapGraphQlIssues(JsonNode nodes) {
        List<Map<String, Object>> mapped = new ArrayList<>();
        if (nodes == null || !nodes.isArray()) {
            return mapped;
        }
        for (JsonNode node : nodes) {
            // A union member we did not spell out in the query comes back as {} —
            // skip rather than emit an item with every field null.
            if (node == null || node.isNull() || !node.has("number")) {
                continue;
            }
            mapped.add(graphQlIssue(node));
        }
        return mapped;
    }

    private static Map<String, Object> graphQlIssue(JsonNode n) {
        JsonNode repository = n.get("repository");
        String fullName = str(repository, "nameWithOwner");
        String[] ownerRepo = splitFullName(fullName);

        List<Map<String, Object>> labels = new ArrayList<>();
        JsonNode labelNodes = n.path("labels").get("nodes");
        if (labelNodes != null && labelNodes.isArray()) {
            for (JsonNode l : labelNodes) {
                Map<String, Object> label = new LinkedHashMap<>();
                label.put("name", str(l, "name"));
                label.put("color", str(l, "color"));
                labels.add(label);
            }
        }

        List<Map<String, Object>> assignees = new ArrayList<>();
        JsonNode assigneeNodes = n.path("assignees").get("nodes");
        if (assigneeNodes != null && assigneeNodes.isArray()) {
            for (JsonNode a : assigneeNodes) {
                Map<String, Object> actor = graphQlActor(a);
                if (actor != null) {
                    assignees.add(actor);
                }
            }
        }

        boolean isPullRequest = "PullRequest".equals(str(n, "__typename"));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", num(n, "databaseId"));
        m.put("number", num(n, "number"));
        m.put("title", str(n, "title"));
        m.put("url", str(n, "url"));
        // GraphQL enums are SCREAMING_CASE (OPEN / NOT_PLANNED); REST is lower case,
        // and the client switches on these values.
        m.put("state", lower(str(n, "state")));
        m.put("stateReason", lower(str(n, "stateReason")));
        m.put("owner", ownerRepo == null ? null : ownerRepo[0]);
        m.put("repo", ownerRepo == null ? null : ownerRepo[1]);
        m.put("repositoryFullName", fullName);
        m.put("labels", labels);
        m.put("createdAt", str(n, "createdAt"));
        m.put("updatedAt", str(n, "updatedAt"));
        m.put("closedAt", str(n, "closedAt"));
        m.put("comments", num(n.get("comments"), "totalCount"));
        m.put("reactions", num(n.get("reactions"), "totalCount"));
        m.put("author", graphQlActor(n.get("author")));
        m.put("assignees", assignees);
        m.put("isPullRequest", isPullRequest);
        m.put("draft", bool(n, "isDraft"));
        JsonNode milestone = n.get("milestone");
        m.put("milestone", milestone == null || milestone.isNull() ? null : str(milestone, "title"));
        m.put("stars", num(repository, "stargazerCount"));
        m.put("language", str(repository == null ? null : repository.get("primaryLanguage"), "name"));
        return m;
    }

    /** GraphQL actors already use camelCase and a bare {@code url}. */
    private static Map<String, Object> graphQlActor(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("login", str(n, "login"));
        m.put("avatarUrl", str(n, "avatarUrl"));
        m.put("url", str(n, "url"));
        return m;
    }

    private static String[] splitFullName(String fullName) {
        if (fullName == null) {
            return null;
        }
        int slash = fullName.indexOf('/');
        return slash < 0 ? null : new String[]{fullName.substring(0, slash), fullName.substring(slash + 1)};
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static Map<String, Object> user(JsonNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("login", str(n, "login"));
        m.put("id", num(n, "id"));
        m.put("type", str(n, "type"));
        m.put("name", str(n, "name"));
        m.put("avatarUrl", str(n, "avatar_url"));
        m.put("url", str(n, "html_url"));
        m.put("bio", str(n, "bio"));
        m.put("location", str(n, "location"));
        m.put("company", str(n, "company"));
        m.put("blog", str(n, "blog"));
        m.put("email", str(n, "email"));
        m.put("publicRepos", num(n, "public_repos"));
        m.put("publicGists", num(n, "public_gists"));
        m.put("followers", num(n, "followers"));
        m.put("following", num(n, "following"));
        m.put("createdAt", str(n, "created_at"));
        m.put("hireable", bool(n, "hireable"));
        return m;
    }

    private static Map<String, Object> code(JsonNode n) {
        JsonNode repo = n.get("repository");
        Map<String, Object> repository = null;
        if (repo != null && !repo.isNull()) {
            repository = new LinkedHashMap<>();
            repository.put("fullName", str(repo, "full_name"));
            repository.put("url", str(repo, "html_url"));
            JsonNode owner = repo.get("owner");
            repository.put("owner", owner == null ? null : str(owner, "login"));
            repository.put("avatarUrl", owner == null ? null : str(owner, "avatar_url"));
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", str(n, "name"));
        m.put("path", str(n, "path"));
        m.put("sha", str(n, "sha"));
        m.put("url", str(n, "html_url"));
        m.put("repository", repository);
        m.put("language", str(n, "language"));
        m.put("fileSize", num(n, "size"));
        return m;
    }

    private static Map<String, Object> commit(JsonNode n) {
        JsonNode commit = n.path("commit");
        JsonNode commitAuthor = commit.path("author");
        JsonNode commitCommitter = commit.path("committer");

        JsonNode repo = n.get("repository");
        Map<String, Object> repository = null;
        if (repo != null && !repo.isNull()) {
            repository = new LinkedHashMap<>();
            repository.put("fullName", str(repo, "full_name"));
            repository.put("url", str(repo, "html_url"));
        }

        String sha = text(n, "sha");
        JsonNode parents = n.get("parents");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sha", sha);
        m.put("shortSha", sha == null ? null : sha.substring(0, Math.min(7, sha.length())));
        m.put("message", str(commit, "message"));
        m.put("url", str(n, "html_url"));
        m.put("author", actor(n.get("author")));
        m.put("authorName", str(commitAuthor, "name"));
        m.put("authorEmail", str(commitAuthor, "email"));
        m.put("authoredAt", str(commitAuthor, "date"));
        m.put("committerName", str(commitCommitter, "name"));
        m.put("committedAt", str(commitCommitter, "date"));
        m.put("repository", repository);
        m.put("commentCount", num(commit, "comment_count"));
        // GitHub does not flag merges directly; more than one parent is what a merge is.
        m.put("isMerge", parents != null && parents.isArray() ? parents.size() > 1 : null);
        return m;
    }

    private static Map<String, Object> topic(JsonNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        String name = str(n, "name");
        m.put("name", name);
        // The topics endpoint returns no URL of its own, but every topic has a
        // canonical page. Deriving it here keeps the client from having to.
        m.put("url", name == null ? null
                : "https://github.com/topics/" + URLEncoder.encode(name, StandardCharsets.UTF_8));
        m.put("displayName", str(n, "display_name"));
        m.put("shortDescription", str(n, "short_description"));
        m.put("description", str(n, "description"));
        m.put("createdBy", str(n, "created_by"));
        m.put("released", str(n, "released"));
        m.put("createdAt", str(n, "created_at"));
        m.put("updatedAt", str(n, "updated_at"));
        m.put("featured", bool(n, "featured"));
        m.put("curated", bool(n, "curated"));
        m.put("repositoryCount", num(n, "repository_count"));
        m.put("logoUrl", str(n, "logo_url"));
        return m;
    }

    private static Map<String, Object> label(JsonNode n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", num(n, "id"));
        m.put("name", str(n, "name"));
        m.put("color", str(n, "color"));
        m.put("description", str(n, "description"));
        m.put("isDefault", bool(n, "default"));
        m.put("url", str(n, "url"));
        return m;
    }

    // ---------------------------------------------------------------- helpers

    /** True when the search hit is really a pull request. */
    public static boolean isPullRequest(JsonNode issue) {
        JsonNode pr = issue.get("pull_request");
        return pr != null && !pr.isNull();
    }

    /** {@code {login, avatarUrl, url}}; null for ghost (deleted) users, which GitHub sends as null. */
    private static Map<String, Object> actor(JsonNode n) {
        if (n == null || n.isNull()) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("login", str(n, "login"));
        m.put("avatarUrl", str(n, "avatar_url"));
        m.put("url", str(n, "html_url"));
        return m;
    }

    private static Object licenseName(JsonNode license) {
        if (license == null || license.isNull()) {
            return null;
        }
        String spdx = text(license, "spdx_id");
        return spdx != null ? spdx : text(license, "key");
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode v : array) {
                values.add(v.asText(null));
            }
        }
        return values;
    }

    private static String str(JsonNode n, String field) {
        return text(n, field);
    }

    private static String text(JsonNode n, String field) {
        if (n == null) {
            return null;
        }
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Object num(JsonNode n, String field) {
        if (n == null) {
            return null;
        }
        JsonNode v = n.get(field);
        return v == null || v.isNull() || !v.isNumber() ? null : v.numberValue();
    }

    private static Object bool(JsonNode n, String field) {
        if (n == null) {
            return null;
        }
        JsonNode v = n.get(field);
        return v == null || v.isNull() || !v.isBoolean() ? null : v.booleanValue();
    }

    /** {@code repository_url} is {@code https://api.github.com/repos/OWNER/REPO}. */
    private static String[] ownerAndRepo(String repositoryUrl) {
        if (repositoryUrl == null) {
            return null;
        }
        int marker = repositoryUrl.indexOf("/repos/");
        if (marker < 0) {
            return null;
        }
        String[] parts = repositoryUrl.substring(marker + "/repos/".length()).split("/");
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return null;
        }
        return new String[]{parts[0], parts[1]};
    }
}
