package org.prebid.server.util;

import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class UriTest {

    @Test
    public void creationShouldThrowNullPointerExceptionWhenUriIsNull() {
        // when and then
        assertThatNullPointerException().isThrownBy(() -> Uri.of(null));
    }

    @Test
    public void creationShouldThrowIllegalArgumentExceptionWhenUriContainsOptionalQueryVariables() {
        // when and then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Uri.of("https://prebid.org{?optional}"))
                .withMessageContaining("does not support optional query variables");
    }

    @Test
    public void creationShouldThrowIllegalArgumentExceptionWhenUriIsNotValid() {
        // when and then
        assertThatIllegalArgumentException().isThrownBy(() -> Uri.of("not a valid url"));
    }

    @Test
    public void creationShouldValidateUrlWithMacrosStripped() {
        // when and then
        assertThat(Uri.of("https://{host}.prebid.org/{path}")).isNotNull();
    }

    @Test
    public void expandShouldPreserveExistingQueryParams() {
        // when and then
        assertThat(Uri.of("https://prebid.org?a=b").expand()).isEqualTo("https://prebid.org?a=b");
    }

    @Test
    public void expandShouldThrowNoSuchElementExceptionWhenMacroIsNotReplaced() {
        // given
        final Uri uri = Uri.of("https://{host}.prebid.org");

        // when and then
        assertThatExceptionOfType(NoSuchElementException.class).isThrownBy(uri::expand);
    }

    @Test
    public void replaceMacroShouldReplaceStringValue() {
        // when and then
        assertThat(Uri.of("https://{host}.prebid.org").replaceMacro("host", "test").expand())
                .isEqualTo("https://test.prebid.org");
    }

    @Test
    public void replaceMacroShouldReplaceListValueAsCommaSeparatedString() {
        // when and then
        assertThat(Uri.of("https://prebid.org/{path}").replaceMacro("path", List.of("a", "b")).expand())
                .isEqualTo("https://prebid.org/a,b");
    }

    @Test
    public void addQueryParamShouldAppendParamWithQuestionMarkWhenUriHasNoQuery() {
        // when and then
        assertThat(Uri.of("https://prebid.org").addQueryParam("key", "value").expand())
                .isEqualTo("https://prebid.org?key=value");
    }

    @Test
    public void addQueryParamShouldAppendParamWithAmpersandWhenUriAlreadyHasQuery() {
        // when and then
        assertThat(Uri.of("https://prebid.org?a=b").addQueryParam("key", "value").expand())
                .isEqualTo("https://prebid.org?a=b&key=value");
    }

    @Test
    public void addQueryParamShouldPreserveInsertionOrderOfMultipleParams() {
        // when and then
        assertThat(Uri.of("https://prebid.org")
                .addQueryParam("a", "1")
                .addQueryParam("b", "2")
                .expand())
                .isEqualTo("https://prebid.org?a=1&b=2");
    }

    @Test
    public void addQueryParamShouldEncodeValue() {
        // when and then
        assertThat(Uri.of("https://prebid.org").addQueryParam("key", "a b&c").expand())
                .isEqualTo("https://prebid.org?key=a%20b%26c");
    }

    @Test
    public void addQueryParamShouldIgnoreNullStringValue() {
        // when and then
        assertThat(Uri.of("https://prebid.org").addQueryParam("key", (String) null).expand())
                .isEqualTo("https://prebid.org");
    }

    @Test
    public void addQueryParamShouldJoinCollectionValueWithCommas() {
        // when and then
        assertThat(Uri.of("https://prebid.org").addQueryParam("key", List.of("a", "b")).expand())
                .isEqualTo("https://prebid.org?key=a%2Cb");
    }

    @Test
    public void addQueryParamShouldIgnoreNullCollectionValue() {
        // when and then
        assertThat(Uri.of("https://prebid.org").addQueryParam("key", (Collection<?>) null).expand())
                .isEqualTo("https://prebid.org");
    }

    @Test
    public void addQueryParamShouldAppendEmptyValueForEmptyCollection() {
        // when and then
        assertThat(Uri.of("https://prebid.org").addQueryParam("key", emptyList()).expand())
                .isEqualTo("https://prebid.org?key=");
    }

    @Test
    public void toStringShouldReturnExpandedUri() {
        // when and then
        assertThat(Uri.of("https://{host}.prebid.org").replaceMacro("host", "test").toString())
                .isEqualTo("https://test.prebid.org");
    }

    @Test
    public void expandShouldEncodeDangerousCharacters() {
        // when and then
        assertThat(Uri.of("https://{inject}.prebid.org").replaceMacro("inject", "malicious.com/a").expand())
                .isEqualTo("https://malicious.com%2Fa.prebid.org");

        assertThat(Uri.of("https://prebid.org.{inject}").replaceMacro("inject", "@malicious.com").expand())
                .isEqualTo("https://prebid.org.%40malicious.com");

        assertThat(Uri.of("https://{inject}.prebid.org").replaceMacro("inject", "malicious.com:8080").expand())
                .isEqualTo("https://malicious.com%3A8080.prebid.org");

        assertThat(Uri.of("https://{inject}.prebid.org").replaceMacro("inject", "malicious.com?a").expand())
                .isEqualTo("https://malicious.com%3Fa.prebid.org");

        assertThat(Uri.of("https://{inject}.prebid.org").replaceMacro("inject", "malicious.com#a").expand())
                .isEqualTo("https://malicious.com%23a.prebid.org");
    }
}
