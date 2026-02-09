package org.example.gdm.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for PatternMatcher.
 */
class PatternMatcherTest {

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should throw when pattern is null")
        void shouldThrowWhenPatternNull() {
            assertThatThrownBy(() -> new PatternMatcher(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("should throw when pattern is empty")
        void shouldThrowWhenPatternEmpty() {
            assertThatThrownBy(() -> new PatternMatcher(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be null or empty");
        }

        @Test
        @DisplayName("should throw when pattern has no colon")
        void shouldThrowWhenPatternHasNoColon() {
            assertThatThrownBy(() -> new PatternMatcher("orgspringframework"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Expected format: groupId:artifactId");
        }

        @Test
        @DisplayName("should throw when groupId part is empty")
        void shouldThrowWhenGroupIdEmpty() {
            assertThatThrownBy(() -> new PatternMatcher(":spring-core"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Both groupId and artifactId parts are required");
        }

        @Test
        @DisplayName("should throw when artifactId part is empty")
        void shouldThrowWhenArtifactIdEmpty() {
            assertThatThrownBy(() -> new PatternMatcher("org.springframework:"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Both groupId and artifactId parts are required");
        }

        @Test
        @DisplayName("should accept valid pattern")
        void shouldAcceptValidPattern() {
            PatternMatcher matcher = new PatternMatcher("org.springframework:spring-core");

            assertThat(matcher.getPattern()).isEqualTo("org.springframework:spring-core");
        }

        @Test
        @DisplayName("should trim whitespace from pattern")
        void shouldTrimWhitespace() {
            PatternMatcher matcher = new PatternMatcher("  org.springframework:spring-core  ");

            assertThat(matcher.getPattern()).isEqualTo("org.springframework:spring-core");
        }
    }

    @Nested
    @DisplayName("Exact Matching")
    class ExactMatching {

        @Test
        @DisplayName("should match exact groupId and artifactId")
        void shouldMatchExact() {
            PatternMatcher matcher = new PatternMatcher("org.springframework:spring-core");

            assertThat(matcher.matches("org.springframework", "spring-core")).isTrue();
        }

        @Test
        @DisplayName("should not match different groupId")
        void shouldNotMatchDifferentGroupId() {
            PatternMatcher matcher = new PatternMatcher("org.springframework:spring-core");

            assertThat(matcher.matches("org.other", "spring-core")).isFalse();
        }

        @Test
        @DisplayName("should not match different artifactId")
        void shouldNotMatchDifferentArtifactId() {
            PatternMatcher matcher = new PatternMatcher("org.springframework:spring-core");

            assertThat(matcher.matches("org.springframework", "spring-web")).isFalse();
        }
    }

    @Nested
    @DisplayName("Asterisk Wildcard (*)")
    class AsteriskWildcard {

        @Test
        @DisplayName("should match any artifactId with *")
        void shouldMatchAnyArtifactId() {
            PatternMatcher matcher = new PatternMatcher("org.springframework:*");

            assertThat(matcher.matches("org.springframework", "spring-core")).isTrue();
            assertThat(matcher.matches("org.springframework", "spring-web")).isTrue();
            assertThat(matcher.matches("org.springframework", "anything")).isTrue();
        }

        @Test
        @DisplayName("should match any groupId with *")
        void shouldMatchAnyGroupId() {
            PatternMatcher matcher = new PatternMatcher("*:junit");

            assertThat(matcher.matches("junit", "junit")).isTrue();
            assertThat(matcher.matches("org.junit", "junit")).isTrue();
            assertThat(matcher.matches("any.group.id", "junit")).isTrue();
        }

        @Test
        @DisplayName("should match both wildcards")
        void shouldMatchBothWildcards() {
            PatternMatcher matcher = new PatternMatcher("*:*");

            assertThat(matcher.matches("any", "thing")).isTrue();
            assertThat(matcher.matches("org.springframework", "spring-core")).isTrue();
        }

        @Test
        @DisplayName("should match prefix wildcard")
        void shouldMatchPrefixWildcard() {
            PatternMatcher matcher = new PatternMatcher("org.springframework:spring-*");

            assertThat(matcher.matches("org.springframework", "spring-core")).isTrue();
            assertThat(matcher.matches("org.springframework", "spring-web")).isTrue();
            assertThat(matcher.matches("org.springframework", "spring-")).isTrue();
            assertThat(matcher.matches("org.springframework", "not-spring")).isFalse();
        }

        @Test
        @DisplayName("should match suffix wildcard")
        void shouldMatchSuffixWildcard() {
            PatternMatcher matcher = new PatternMatcher("org.springframework:*-core");

            assertThat(matcher.matches("org.springframework", "spring-core")).isTrue();
            assertThat(matcher.matches("org.springframework", "some-core")).isTrue();
            assertThat(matcher.matches("org.springframework", "core")).isFalse(); // needs prefix
        }

        @Test
        @DisplayName("should match middle wildcard")
        void shouldMatchMiddleWildcard() {
            PatternMatcher matcher = new PatternMatcher("org.springframework:spring-*-starter");

            assertThat(matcher.matches("org.springframework", "spring-boot-starter")).isTrue();
            assertThat(matcher.matches("org.springframework", "spring-web-starter")).isTrue();
            assertThat(matcher.matches("org.springframework", "spring--starter")).isTrue();
            assertThat(matcher.matches("org.springframework", "spring-starter")).isFalse();
        }

        @Test
        @DisplayName("asterisk should match empty string")
        void asteriskShouldMatchEmptyString() {
            PatternMatcher matcher = new PatternMatcher("com.example:my-*-app");

            assertThat(matcher.matches("com.example", "my--app")).isTrue();
        }
    }

    @Nested
    @DisplayName("Question Mark Wildcard (?)")
    class QuestionMarkWildcard {

        @Test
        @DisplayName("should match exactly one character")
        void shouldMatchExactlyOneCharacter() {
            PatternMatcher matcher = new PatternMatcher("com.company:my-?-app");

            assertThat(matcher.matches("com.company", "my-1-app")).isTrue();
            assertThat(matcher.matches("com.company", "my-2-app")).isTrue();
            assertThat(matcher.matches("com.company", "my-a-app")).isTrue();
        }

        @Test
        @DisplayName("should not match zero characters")
        void shouldNotMatchZeroCharacters() {
            PatternMatcher matcher = new PatternMatcher("com.company:my-?-app");

            assertThat(matcher.matches("com.company", "my--app")).isFalse();
        }

        @Test
        @DisplayName("should not match multiple characters")
        void shouldNotMatchMultipleCharacters() {
            PatternMatcher matcher = new PatternMatcher("com.company:my-?-app");

            assertThat(matcher.matches("com.company", "my-10-app")).isFalse();
            assertThat(matcher.matches("com.company", "my-abc-app")).isFalse();
        }

        @Test
        @DisplayName("should support multiple question marks")
        void shouldSupportMultipleQuestionMarks() {
            PatternMatcher matcher = new PatternMatcher("com.example:app-??");

            assertThat(matcher.matches("com.example", "app-12")).isTrue();
            assertThat(matcher.matches("com.example", "app-ab")).isTrue();
            assertThat(matcher.matches("com.example", "app-1")).isFalse();
            assertThat(matcher.matches("com.example", "app-123")).isFalse();
        }
    }

    @Nested
    @DisplayName("Combined Wildcards")
    class CombinedWildcards {

        @Test
        @DisplayName("should combine * and ?")
        void shouldCombineAsteriskAndQuestion() {
            PatternMatcher matcher = new PatternMatcher("org.*:spring-?ore");

            assertThat(matcher.matches("org.springframework", "spring-core")).isTrue();
            assertThat(matcher.matches("org.example", "spring-bore")).isTrue();
            assertThat(matcher.matches("org.", "spring-core")).isTrue(); // * matches empty string, so org. matches org.*
            assertThat(matcher.matches("orgspringframework", "spring-core")).isFalse(); // dot required (not a wildcard)
        }
    }

    @Nested
    @DisplayName("Special Characters")
    class SpecialCharacters {

        @Test
        @DisplayName("should escape dots in groupId")
        void shouldEscapeDotsInGroupId() {
            PatternMatcher matcher = new PatternMatcher("org.springframework:spring-core");

            // Dot should not match any character
            assertThat(matcher.matches("orgXspringframework", "spring-core")).isFalse();
        }

        @Test
        @DisplayName("should handle hyphens correctly")
        void shouldHandleHyphensCorrectly() {
            PatternMatcher matcher = new PatternMatcher("com.example:my-app-name");

            assertThat(matcher.matches("com.example", "my-app-name")).isTrue();
            assertThat(matcher.matches("com.example", "myXappXname")).isFalse();
        }

        @Test
        @DisplayName("should handle underscores correctly")
        void shouldHandleUnderscoresCorrectly() {
            PatternMatcher matcher = new PatternMatcher("com.example:my_app_name");

            assertThat(matcher.matches("com.example", "my_app_name")).isTrue();
        }
    }

    @Nested
    @DisplayName("Null Handling")
    class NullHandling {

        @Test
        @DisplayName("should return false for null groupId")
        void shouldReturnFalseForNullGroupId() {
            PatternMatcher matcher = new PatternMatcher("org.springframework:*");

            assertThat(matcher.matches(null, "spring-core")).isFalse();
        }

        @Test
        @DisplayName("should return false for null artifactId")
        void shouldReturnFalseForNullArtifactId() {
            PatternMatcher matcher = new PatternMatcher("org.springframework:*");

            assertThat(matcher.matches("org.springframework", null)).isFalse();
        }
    }

    @Nested
    @DisplayName("Parameterized Tests")
    class ParameterizedTests {

        @ParameterizedTest
        @DisplayName("should match expected patterns")
        @CsvSource({
                "org.springframework:*, org.springframework, spring-core, true",
                "org.springframework:*, org.springframework, spring-web, true",
                "org.springframework:*, org.other, spring-core, false",
                "*:junit, junit, junit, true",
                "*:junit, org.junit, junit, true",
                "*:junit, org.junit, not-junit, false",
                "com.company:my-?-app, com.company, my-1-app, true",
                "com.company:my-?-app, com.company, my-10-app, false",
                "*:*-test, com.example, my-test, true",
                "*:*-test, com.example, test, false",
                "org.springframework.boot:spring-boot-starter-*, org.springframework.boot, spring-boot-starter-web, true",
                "org.springframework.boot:spring-boot-starter-*, org.springframework.boot, spring-boot-starter, false"
        })
        void shouldMatchPatterns(String pattern, String groupId, String artifactId, boolean expected) {
            PatternMatcher matcher = new PatternMatcher(pattern);

            assertThat(matcher.matches(groupId, artifactId)).isEqualTo(expected);
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal for same pattern")
        void shouldBeEqualForSamePattern() {
            PatternMatcher matcher1 = new PatternMatcher("org.springframework:*");
            PatternMatcher matcher2 = new PatternMatcher("org.springframework:*");

            assertThat(matcher1).isEqualTo(matcher2);
            assertThat(matcher1.hashCode()).isEqualTo(matcher2.hashCode());
        }

        @Test
        @DisplayName("should not be equal for different patterns")
        void shouldNotBeEqualForDifferentPatterns() {
            PatternMatcher matcher1 = new PatternMatcher("org.springframework:*");
            PatternMatcher matcher2 = new PatternMatcher("org.other:*");

            assertThat(matcher1).isNotEqualTo(matcher2);
        }
    }
}

