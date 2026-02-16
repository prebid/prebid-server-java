package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.optable.targeting.model.Id;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class QueryBuilderTest {

    private final OptableAttributes optableAttributes = givenOptableAttributes();

    private final String idPrefixOrder = "c,c1";

    @Test
    public void shouldSeparateAttributesFromIds() {
        // given
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"), Id.of(Id.PHONE, "123"));

        // when
        final Query query = QueryBuilder.build(ids, optableAttributes, idPrefixOrder);

        // then
        assertThat(query.getIds()).isEqualTo("&id=e%3Aemail&id=p%3A123");
        assertThat(query.getAttributes()).isEqualTo("&gdpr_consent=tcf&gdpr=1&timeout=100ms&osdk=prebid-server");
    }

    @Test
    public void shouldBuildFullQueryString() {
        // given
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"), Id.of(Id.PHONE, "123"));

        // when
        final Query query = QueryBuilder.build(ids, optableAttributes, idPrefixOrder);

        // then
        assertThat(query.getIds()).isEqualTo("&id=e%3Aemail&id=p%3A123");
        assertThat(query.getAttributes()).isEqualTo("&gdpr_consent=tcf&gdpr=1&timeout=100ms&osdk=prebid-server");
        assertThat(query.toQueryString())
                .isEqualTo("&id=e%3Aemail&id=p%3A123&gdpr_consent=tcf&gdpr=1&timeout=100ms&osdk=prebid-server");
    }

    @Test
    public void shouldBuildQueryStringWhenHaveIds() {
        // given
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"), Id.of(Id.PHONE, "123"));

        // when
        final String query = QueryBuilder.build(ids, optableAttributes, idPrefixOrder).toQueryString();

        // then
        assertThat(query).contains("e%3Aemail", "p%3A123");
    }

    @Test
    public void shouldBuildQueryStringWithExtraAttributes() {
        // given
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"), Id.of(Id.PHONE, "123"));

        // when
        final String query = QueryBuilder.build(ids, optableAttributes, idPrefixOrder).toQueryString();

        // then
        assertThat(query).contains("&gdpr=1", "&gdpr_consent=tcf", "&timeout=100ms");
    }

    @Test
    public void shouldBuildQueryStringWithRightOrder() {
        // given
        final List<Id> ids = List.of(
                Id.of(Id.ID5, "ID5"),
                Id.of(Id.EMAIL, "email"),
                Id.of("c1", "123"),
                Id.of("c", "234"));

        // when
        final String query = QueryBuilder.build(ids, optableAttributes, idPrefixOrder).toQueryString();

        // then
        assertThat(query).startsWith("&id=c%3A234&id=c1%3A123&id=id5%3AID5&id=e%3Aemail");
    }

    @Test
    public void shouldBuildQueryStringWhenIdsListIsEmptyAndIpIsPresent() {
        // given
        final List<Id> ids = List.of();
        final OptableAttributes attributes = OptableAttributes.builder()
                .ips(List.of("8.8.8.8"))
                .build();

        // when
        final Query query = QueryBuilder.build(ids, attributes, idPrefixOrder);

        // then
        assertThat(query).isNotNull();
        assertThat(query.toQueryString()).isEqualTo("&gdpr=0&osdk=prebid-server");
    }

    @Test
    public void shouldNotBuildQueryStringWhenIdsListIsEmptyAndIpIsAbsent() {
        // given
        final List<Id> ids = List.of();
        final OptableAttributes attributes = OptableAttributes.builder().build();

        // when
        final Query query = QueryBuilder.build(ids, attributes, idPrefixOrder);

        // then
        assertThat(query).isNull();
    }

    private OptableAttributes givenOptableAttributes() {
        return OptableAttributes.builder()
                .timeout(100L)
                .gdprApplies(true)
                .gdprConsent("tcf")
                .build();
    }
}
