package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.optable.targeting.model.Id;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class QueryBuilderTest {

    private QueryBuilder target;

    private OptableAttributes optableAttributes;

    @BeforeEach
    public void setUp() {
        optableAttributes = givenOptableAttributes();
        target = new QueryBuilder("c,c1");
    }

    @Test
    public void shouldBuildQueryStringWhenHaveIds() {
        // given
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"), Id.of(Id.PHONE, "123"));

        // when
        final String query = target.build(ids, optableAttributes);

        // then
        assertThat(query).contains("e%3Aemail", "p%3A123");
    }

    @Test
    public void shouldBuildQueryStringWithExtraAttributes() {
        // given
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"), Id.of(Id.PHONE, "123"));

        // when
        final String query = target.build(ids, optableAttributes);

        // then
        assertThat(query).contains("&reg=gdpr", "&tcf=tcf", "&timeout=100ms");
    }

    @Test
    public void shouldBuildQueryStringWithRightOrder() {
        // given
        final List<Id> ids = List.of(Id.of(Id.ID5, "ID5"), Id.of(Id.EMAIL, "email"), Id.of("c1", "123"),
                Id.of("c", "234"));

        // when
        final String query = target.build(ids, optableAttributes);

        // then
        assertThat(query).startsWith("c%3A234&id=c1%3A123&id=id5%3AID5&id=e%3Aemail");
    }

    @Test
    public void shouldNotBuildQueryStringWhenIdsListIsEmpty() {
        // given
        final List<Id> ids = List.of();

        // when
        final String query = target.build(ids, optableAttributes);

        // then

        assertThat(query).isNull();
    }

    private OptableAttributes givenOptableAttributes() {
        return OptableAttributes.of("gdpr").toBuilder()
                .timeout(100L)
                .tcf("tcf")
                .build();
    }
}
