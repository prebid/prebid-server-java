package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.optable.targeting.model.App;
import org.prebid.server.hooks.modules.optable.targeting.model.Id;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.Query;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class QueryBuilderTest {

    private final OptableAttributes optableAttributes = givenOptableAttributes();

    private final String idPrefixOrder = "c,c1";

    private OptableTargetingProperties properties() {
        return givenProperties(idPrefixOrder, null);
    }

    @Test
    public void shouldSeparateAttributesFromIds() {
        // given
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"), Id.of(Id.PHONE, "123"));

        // when
        final Query query = QueryBuilder.build(ids, optableAttributes, properties());

        // then
        assertThat(query.getIds()).isEqualTo("&id=e%3Aemail&id=p%3A123");
        assertThat(query.getHid()).isEqualTo("");
        assertThat(query.getAttributes()).isEqualTo("&gdpr_consent=tcf&gdpr=1&timeout=100ms&osdk=prebid-server");
    }

    @Test
    public void shouldBuildFullQueryString() {
        // given
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"), Id.of(Id.PHONE, "123"));

        // when
        final Query query = QueryBuilder.build(ids, optableAttributes, properties());

        // then
        assertThat(query.getIds()).isEqualTo("&id=e%3Aemail&id=p%3A123");
        assertThat(query.getHid()).isEqualTo("");
        assertThat(query.getAttributes()).isEqualTo("&gdpr_consent=tcf&gdpr=1&timeout=100ms&osdk=prebid-server");
        assertThat(query.toQueryString())
                .isEqualTo("&id=e%3Aemail&id=p%3A123&gdpr_consent=tcf&gdpr=1&timeout=100ms&osdk=prebid-server");
    }

    @Test
    public void shouldBuildQueryStringWhenHaveIds() {
        // given
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"), Id.of(Id.PHONE, "123"));

        // when
        final String query = QueryBuilder.build(ids, optableAttributes, properties()).toQueryString();

        // then
        assertThat(query).contains("e%3Aemail", "p%3A123");
    }

    @Test
    public void shouldBuildQueryStringWithExtraAttributes() {
        // given
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"), Id.of(Id.PHONE, "123"));

        // when
        final String query = QueryBuilder.build(ids, optableAttributes, properties()).toQueryString();

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
        final String query = QueryBuilder.build(ids, optableAttributes, properties()).toQueryString();

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
        final Query query = QueryBuilder.build(ids, attributes, properties());

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
        final Query query = QueryBuilder.build(ids, attributes, properties());

        // then
        assertThat(query).isNull();
    }

    @Test
    public void shouldBuildQueryStringWithGppSid() {
        // given
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"));
        final OptableAttributes attributes = OptableAttributes.builder()
                .ips(List.of("8.8.8.8"))
                .gpp("DBABzw~1YNY~BVQqAAAAAgA")
                .gppSid(Set.of(7, 22))
                .build();

        // when
        final String query = QueryBuilder.build(ids, attributes, properties()).toQueryString();

        // then
        assertThat(query).contains("&gpp=DBABzw~1YNY~BVQqAAAAAgA");
        final String gppSidValue = query.split("gpp_sid=")[1].split("&")[0];
        assertThat(gppSidValue.split(",")).containsExactlyInAnyOrder("7", "22");
        assertThat(query).doesNotContain("Optional");
    }

    @Test
    public void shouldBuildQueryStringWithSingleGppSid() {
        // given
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"));
        final OptableAttributes attributes = OptableAttributes.builder()
                .ips(List.of("8.8.8.8"))
                .gpp("DBABzw~1YNY")
                .gppSid(Set.of(7))
                .build();

        // when
        final String query = QueryBuilder.build(ids, attributes, properties()).toQueryString();

        // then
        assertThat(query).contains("&gpp_sid=7");
        assertThat(query).doesNotContain("Optional");
    }

    @Test
    public void shouldLimitGppSidToTwoValues() {
        // given
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"));
        final OptableAttributes attributes = OptableAttributes.builder()
                .ips(List.of("8.8.8.8"))
                .gpp("DBABzw~1YNY~BVQqAAAAAgA")
                .gppSid(Set.of(5, 7, 22))
                .build();

        // when
        final String query = QueryBuilder.build(ids, attributes, properties()).toQueryString();

        // then
        final String gppSidValue = query.split("gpp_sid=")[1].split("&")[0];
        assertThat(gppSidValue.split(",")).hasSize(2);
        assertThat(query).doesNotContain("Optional");
    }

    @Test
    public void shouldNotIncludeGppSidWhenEmpty() {
        // given
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"));
        final OptableAttributes attributes = OptableAttributes.builder()
                .ips(List.of("8.8.8.8"))
                .gpp("DBABzw~1YNY")
                .gppSid(Set.of())
                .build();

        // when
        final String query = QueryBuilder.build(ids, attributes, properties()).toQueryString();

        // then
        assertThat(query).doesNotContain("gpp_sid");
    }

    @Test
    public void shouldBuildHidWhenHidPrefixesMatchIds() {
        // given
        final OptableTargetingProperties props = givenProperties(null, "c,i6");
        final List<Id> ids = List.of(
                Id.of("c", "234"),
                Id.of(Id.DEVICE_IP_V_6, "0:0:0:0:0:0:0:1"));

        // when
        final Query query = QueryBuilder.build(ids, optableAttributes, props);

        // then
        assertThat(query.getHid()).isEqualTo("&hid=c:234&hid=i6:0:0:0:0:0:0:0:1");
    }

    @Test
    public void shouldExcludeDeviceIpV6FromIdsString() {
        // given
        final OptableTargetingProperties props = givenProperties(null, "i6");
        final List<Id> ids = List.of(
                Id.of(Id.EMAIL, "email"),
                Id.of(Id.DEVICE_IP_V_6, "0:0:0:0:0:0:0:1"));

        // when
        final Query query = QueryBuilder.build(ids, optableAttributes, props);

        // then
        assertThat(query.getIds()).doesNotContain(Id.DEVICE_IP_V_6);
        assertThat(query.getHid()).isEqualTo("&hid=i6:0:0:0:0:0:0:0:1");
    }

    @Test
    public void shouldNotBuildHidWhenNoMatch() {
        // given
        final OptableTargetingProperties props = givenProperties(null, "nonexistent");
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"));

        // when
        final Query query = QueryBuilder.build(ids, optableAttributes, props);

        // then
        assertThat(query.getHid()).isEqualTo("");
    }

    @Test
    public void shouldNotBuildHidWhenHidPrefixesNotConfigured() {
        // given
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"));

        // when
        final Query query = QueryBuilder.build(ids, optableAttributes, properties());

        // then
        assertThat(query.getHid()).isEqualTo("");
    }

    @Test
    public void shouldAppendBundleAndVerWhenAppHasBoth() {
        // given
        final OptableAttributes attributes = OptableAttributes.builder()
                .app(App.of("com.example.app", "1.2.3"))
                .build();
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"));

        // when
        final String query = QueryBuilder.build(ids, attributes, properties()).toQueryString();

        // then
        assertThat(query).contains("&bundle=com.example.app", "&ver=1.2.3");
    }

    @Test
    public void shouldAppendBundleOnlyWhenVerIsEmpty() {
        // given
        final OptableAttributes attributes = OptableAttributes.builder()
                .app(App.of("com.example.app", ""))
                .build();
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"));

        // when
        final String query = QueryBuilder.build(ids, attributes, properties()).toQueryString();

        // then
        assertThat(query).contains("&bundle=com.example.app");
        assertThat(query).doesNotContain("&ver=");
    }

    @Test
    public void shouldNotAppendBundleAndVerWhenBundleIsEmpty() {
        // given
        final OptableAttributes attributes = OptableAttributes.builder()
                .app(App.of("", "1.2.3"))
                .build();
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"));

        // when
        final String query = QueryBuilder.build(ids, attributes, properties()).toQueryString();

        // then
        assertThat(query).doesNotContain("&bundle=", "&ver=");
    }

    @Test
    public void shouldNotAppendBundleAndVerWhenAppIsNull() {
        // given
        final OptableAttributes attributes = OptableAttributes.builder().build();
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"));

        // when
        final String query = QueryBuilder.build(ids, attributes, properties()).toQueryString();

        // then
        assertThat(query).doesNotContain("&bundle=", "&ver=");
    }

    @Test
    public void shouldAppendId5SignatureWhenPresent() {
        // given
        final OptableAttributes attributes = OptableAttributes.builder()
                .id5Signature("signature")
                .build();
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"));

        // when
        final String query = QueryBuilder.build(ids, attributes, properties()).toQueryString();

        // then
        assertThat(query).contains("&id5_signature=signature");
    }

    @Test
    public void shouldNotAppendId5SignatureWhenNull() {
        // given
        final OptableAttributes attributes = OptableAttributes.builder().build();
        final List<Id> ids = List.of(Id.of(Id.EMAIL, "email"));

        // when
        final String query = QueryBuilder.build(ids, attributes, properties()).toQueryString();

        // then
        assertThat(query).doesNotContain("&id5_signature=");
    }

    private OptableAttributes givenOptableAttributes() {
        return OptableAttributes.builder()
                .timeout(100L)
                .gdprApplies(true)
                .gdprConsent("tcf")
                .build();
    }

    private static OptableTargetingProperties givenProperties(String idPrefixOrder, String hidPrefixes) {
        final OptableTargetingProperties properties = new OptableTargetingProperties();
        properties.setIdPrefixOrder(idPrefixOrder);
        properties.setHidPrefixes(hidPrefixes);
        return properties;
    }
}
