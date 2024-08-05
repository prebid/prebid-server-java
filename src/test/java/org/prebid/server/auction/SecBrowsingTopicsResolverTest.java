package org.prebid.server.auction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.SecBrowsingTopic;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

public class SecBrowsingTopicsResolverTest {

    private SecBrowsingTopicsResolver target;

    @BeforeEach
    public void setUp() {
        target = new SecBrowsingTopicsResolver("topicsDomain");
    }

    @Test
    public void resolveShouldReturnEmptyListOnMissedHeader() {
        // given
        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.empty();
        final List<String> warnings = new ArrayList<>();

        // when
        final List<SecBrowsingTopic> topics = target.resolve(headers, false, warnings);

        // then
        assertThat(topics).isEmpty();
        assertThat(warnings).isEmpty();
    }

    @Test
    public void resolveShouldReturnValidResult() {
        // given
        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add(
                        HttpUtil.SEC_BROWSING_TOPICS_HEADER,
                        "(1 2 3);v=chrome.configuration_version:2:model_version")
                .build();
        final List<String> warnings = new ArrayList<>();

        // when
        final List<SecBrowsingTopic> topics = target.resolve(headers, false, warnings);

        // then
        assertThat(topics).containsExactly(SecBrowsingTopic.of(
                "topicsDomain",
                Set.of("1", "2", "3"),
                2,
                "model_version"));
        assertThat(warnings).isEmpty();
    }

    @Test
    public void resolveShouldLimitFields() {
        // given
        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add(HttpUtil.SEC_BROWSING_TOPICS_HEADER, """
                        (1);v=chrome.1:2:3,
                        (2);v=chrome.1:2:3,
                        (3);v=chrome.1:2:3,
                        (4);v=chrome.1:2:3,
                        (5);v=chrome.1:2:3,
                        (6);v=chrome.1:2:3,
                        (7);v=chrome.1:2:3,
                        (8);v=chrome.1:2:3,
                        (9);v=chrome.1:2:3,
                        (10);v=chrome.1:2:3,
                        (11);v=chrome.1:2:3
                        """)
                .build();
        final List<String> warnings = new ArrayList<>();

        // when
        final List<SecBrowsingTopic> topics = target.resolve(headers, false, warnings);

        // then
        assertThat(topics)
                .flatExtracting(SecBrowsingTopic::getSegments)
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        assertThat(warnings).isEmpty();
    }

    @Test
    public void resolveShouldLimitAndLogExtraFieldsIfDebugEnabled() {
        // given
        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add(HttpUtil.SEC_BROWSING_TOPICS_HEADER, """
                        (1);v=chrome.1:2:3,
                        (2);v=chrome.1:2:3,
                        (3);v=chrome.1:2:3,
                        (4);v=chrome.1:2:3,
                        (5);v=chrome.1:2:3,
                        (6);v=chrome.1:2:3,
                        (7);v=chrome.1:2:3,
                        (8);v=chrome.1:2:3,
                        (9);v=chrome.1:2:3,
                        (10);v=chrome.1:2:3,
                        (11);v=chrome.1:2:3
                        """)
                .build();
        final List<String> warnings = new ArrayList<>();

        // when
        final List<SecBrowsingTopic> topics = target.resolve(headers, true, warnings);

        // then
        assertThat(topics)
                .flatExtracting(SecBrowsingTopic::getSegments)
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");
        assertThat(warnings).containsExactly(
                "Invalid field in Sec-Browsing-Topics header: \n(11);v=chrome.1:2:3\n discarded due to limit reached.");
    }

    @Test
    public void resolveShouldSkipPaddingField() {
        // given
        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add(HttpUtil.SEC_BROWSING_TOPICS_HEADER, "(1);v=chrome.1:2:3, ();p=P")
                .build();
        final List<String> warnings = new ArrayList<>();

        // when
        final List<SecBrowsingTopic> topics = target.resolve(headers, false, warnings);

        // then
        assertThat(topics).containsExactly(SecBrowsingTopic.of(
                "topicsDomain",
                singleton("1"),
                2,
                "3"));
        assertThat(warnings).isEmpty();
    }

    @Test
    public void resolveShouldSkipInvalidFieldIfDebugDisabled() {
        // given
        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add(HttpUtil.SEC_BROWSING_TOPICS_HEADER, "(1);v=chrome.1:2:3, invalid, ();p=P")
                .build();
        final List<String> warnings = new ArrayList<>();

        // when
        final List<SecBrowsingTopic> topics = target.resolve(headers, false, warnings);

        // then
        assertThat(topics).containsExactly(SecBrowsingTopic.of(
                "topicsDomain",
                singleton("1"),
                2,
                "3"));
        assertThat(warnings).isEmpty();
    }

    @Test
    public void resolveShouldLogInvalidFieldIfDebugEnabled() {
        // given
        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add(HttpUtil.SEC_BROWSING_TOPICS_HEADER, "(1);v=chrome.1:2:3, invalid, ();p=P")
                .build();
        final List<String> warnings = new ArrayList<>();

        // when
        final List<SecBrowsingTopic> topics = target.resolve(headers, true, warnings);

        // then
        assertThat(topics).containsExactly(SecBrowsingTopic.of(
                "topicsDomain",
                singleton("1"),
                2,
                "3"));
        assertThat(warnings).containsExactly("Invalid field in Sec-Browsing-Topics header: invalid");
    }

    @Test
    public void resolveShouldLogInvalidFieldWithIntegerOverflowIfDebugEnabled() {
        // given
        final CaseInsensitiveMultiMap headers = CaseInsensitiveMultiMap.builder()
                .add(HttpUtil.SEC_BROWSING_TOPICS_HEADER, "(9999999999);v=chrome.1:2:3")
                .build();
        final List<String> warnings = new ArrayList<>();

        // when
        final List<SecBrowsingTopic> topics = target.resolve(headers, true, warnings);

        // then
        assertThat(topics).isEmpty();
        assertThat(warnings).containsExactly(
                "Invalid field in Sec-Browsing-Topics header: (9999999999);v=chrome.1:2:3");
    }
}
