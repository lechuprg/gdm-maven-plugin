package org.example.gdm.filter;

import java.util.regex.Pattern;

/**
 * Matches Maven artifact coordinates against glob-style patterns.
 *
 * <p>Pattern format: {@code groupId:artifactId}</p>
 *
 * <p>Wildcards:</p>
 * <ul>
 *   <li>{@code *} - matches zero or more characters</li>
 *   <li>{@code ?} - matches exactly one character</li>
 * </ul>
 *
 * <p>Examples:</p>
 * <ul>
 *   <li>{@code org.springframework:*} - all Spring artifacts</li>
 *   <li>{@code *:junit} - junit from any group</li>
 *   <li>{@code com.company:my-?-app} - matches my-1-app, my-2-app, etc.</li>
 * </ul>
 */
public class PatternMatcher {

    private final String originalPattern;
    private final Pattern groupIdPattern;
    private final Pattern artifactIdPattern;

    /**
     * Creates a new PatternMatcher for the given pattern.
     *
     * @param pattern the glob pattern in format "groupId:artifactId"
     * @throws IllegalArgumentException if pattern is invalid
     */
    public PatternMatcher(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Pattern cannot be null or empty");
        }

        this.originalPattern = pattern.trim();

        int colonIndex = originalPattern.indexOf(':');
        if (colonIndex == -1) {
            throw new IllegalArgumentException(
                    "Invalid pattern format: '" + pattern + "'. Expected format: groupId:artifactId");
        }

        String groupIdGlob = originalPattern.substring(0, colonIndex);
        String artifactIdGlob = originalPattern.substring(colonIndex + 1);

        if (groupIdGlob.isEmpty() || artifactIdGlob.isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid pattern format: '" + pattern + "'. Both groupId and artifactId parts are required");
        }

        this.groupIdPattern = globToRegex(groupIdGlob);
        this.artifactIdPattern = globToRegex(artifactIdGlob);
    }

    /**
     * Tests if the given coordinates match this pattern.
     *
     * @param groupId    the group ID to match
     * @param artifactId the artifact ID to match
     * @return true if both groupId and artifactId match the pattern
     */
    public boolean matches(String groupId, String artifactId) {
        if (groupId == null || artifactId == null) {
            return false;
        }
        return groupIdPattern.matcher(groupId).matches() &&
               artifactIdPattern.matcher(artifactId).matches();
    }

    /**
     * Returns the original pattern string.
     */
    public String getPattern() {
        return originalPattern;
    }

    /**
     * Converts a glob pattern to a regex Pattern.
     *
     * <p>Conversion rules:</p>
     * <ul>
     *   <li>{@code *} becomes {@code .*} (zero or more characters)</li>
     *   <li>{@code ?} becomes {@code .} (exactly one character)</li>
     *   <li>All other regex special characters are escaped</li>
     * </ul>
     */
    private Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder();
        regex.append("^");

        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append(".");
                    break;
                // Escape regex special characters
                case '.':
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                case '|':
                case '^':
                case '$':
                case '+':
                case '\\':
                    regex.append("\\").append(c);
                    break;
                default:
                    regex.append(c);
            }
        }

        regex.append("$");
        return Pattern.compile(regex.toString());
    }

    @Override
    public String toString() {
        return "PatternMatcher{" + originalPattern + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PatternMatcher that = (PatternMatcher) o;
        return originalPattern.equals(that.originalPattern);
    }

    @Override
    public int hashCode() {
        return originalPattern.hashCode();
    }
}

