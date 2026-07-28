package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.Ortb2;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.TargetingResult;
import org.prebid.server.hooks.modules.optable.targeting.model.openrtb.User;
import org.prebid.server.json.ObjectMapperProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class Id5ResolverTest {

    private ObjectMapper mapper;

    @BeforeEach
    public void setUp() {
        mapper = ObjectMapperProvider.mapper();
    }

    @Test
    public void shouldReturnNullWhenTargetingResultIsNull() {
        // when
        final String result = Id5Resolver.resolveId5Signature(null);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void shouldReturnNullWhenOrtb2IsNull() {
        // given
        final TargetingResult targetingResult = new TargetingResult(List.of(), null, null);

        // when
        final String result = Id5Resolver.resolveId5Signature(targetingResult);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void shouldReturnNullWhenUserHasNoEids() {
        // given
        final TargetingResult targetingResult = new TargetingResult(
                List.of(),
                new Ortb2(new User(null, null)),
                null);

        // when
        final String result = Id5Resolver.resolveId5Signature(targetingResult);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void shouldReturnNullWhenEidsDoNotMatchOptableAndId5Source() {
        // given
        final Eid eid = Eid.builder()
                .source("other-source")
                .inserter("other-inserter")
                .uids(List.of(Uid.builder().id("id").build()))
                .build();
        final TargetingResult targetingResult = new TargetingResult(
                List.of(),
                new Ortb2(new User(List.of(eid), null)),
                null);

        // when
        final String result = Id5Resolver.resolveId5Signature(targetingResult);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void shouldReturnNullWhenMatchingEidButRefsAreAbsent() {
        // given
        final ObjectNode uidExt = mapper.createObjectNode();
        uidExt.set("optable", mapper.createObjectNode()
                .set("ref", TextNode.valueOf("refValue")));

        final Eid eid = Eid.builder()
                .source("id5-sync.com")
                .inserter("optable.co")
                .uids(List.of(Uid.builder().id("id").ext(uidExt).build()))
                .build();
        final TargetingResult targetingResult = new TargetingResult(
                List.of(),
                new Ortb2(new User(List.of(eid), null)),
                null);

        // when
        final String result = Id5Resolver.resolveId5Signature(targetingResult);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void shouldReturnNullWhenRefsDoNotContainResolvedRef() {
        // given
        final ObjectNode uidExt = mapper.createObjectNode();
        uidExt.set("optable", mapper.createObjectNode()
                .set("ref", TextNode.valueOf("refValue")));

        final Eid eid = Eid.builder()
                .source("id5-sync.com")
                .inserter("optable.co")
                .uids(List.of(Uid.builder().id("id").ext(uidExt).build()))
                .build();
        final ObjectNode refs = mapper.createObjectNode();
        refs.set("otherRef", mapper.createObjectNode()
                .set("signature", TextNode.valueOf("signatureValue")));
        final TargetingResult targetingResult = new TargetingResult(
                List.of(),
                new Ortb2(new User(List.of(eid), null)),
                refs);

        // when
        final String result = Id5Resolver.resolveId5Signature(targetingResult);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void shouldReturnSignatureWhenAllConditionsAreMet() {
        // given
        final ObjectNode uidExt = mapper.createObjectNode();
        uidExt.set("optable", mapper.createObjectNode()
                .set("ref", TextNode.valueOf("refValue")));

        final Eid eid = Eid.builder()
                .source("id5-sync.com")
                .inserter("optable.co")
                .uids(List.of(Uid.builder().id("id").ext(uidExt).build()))
                .build();
        final ObjectNode refs = mapper.createObjectNode();
        refs.set("refValue", mapper.createObjectNode()
                .set("signature", TextNode.valueOf("signatureValue")));
        final TargetingResult targetingResult = new TargetingResult(
                List.of(),
                new Ortb2(new User(List.of(eid), null)),
                refs);

        // when
        final String result = Id5Resolver.resolveId5Signature(targetingResult);

        // then
        assertThat(result).isEqualTo("signatureValue");
    }

    @Test
    public void shouldReturnSignatureWhenMultipleMatchingEidsExist() {
        // given
        final ObjectNode firstUidExt = mapper.createObjectNode();
        firstUidExt.set("optable", mapper.createObjectNode()
                .set("ref", TextNode.valueOf("firstRef")));

        final ObjectNode secondUidExt = mapper.createObjectNode();
        secondUidExt.set("optable", mapper.createObjectNode()
                .set("ref", TextNode.valueOf("secondRef")));

        final Eid firstEid = Eid.builder()
                .source("id5-sync.com")
                .inserter("optable.co")
                .uids(List.of(Uid.builder().id("id1").ext(firstUidExt).build()))
                .build();
        final Eid secondEid = Eid.builder()
                .source("id5-sync.com")
                .inserter("optable.co")
                .uids(List.of(Uid.builder().id("id2").ext(secondUidExt).build()))
                .build();
        final ObjectNode refs = mapper.createObjectNode();
        refs.set("firstRef", mapper.createObjectNode()
                .set("signature", TextNode.valueOf("firstSignature")));
        refs.set("secondRef", mapper.createObjectNode()
                .set("signature", TextNode.valueOf("secondSignature")));
        final TargetingResult targetingResult = new TargetingResult(
                List.of(),
                new Ortb2(new User(List.of(firstEid, secondEid), null)),
                refs);

        // when
        final String result = Id5Resolver.resolveId5Signature(targetingResult);

        // then
        assertThat(result).isEqualTo("firstSignature");
    }

    @Test
    public void shouldReturnSignatureFromSecondMatchingEidWhenFirstRefNotInRefs() {
        // given
        final ObjectNode firstUidExt = mapper.createObjectNode();
        firstUidExt.set("optable", mapper.createObjectNode()
                .set("ref", TextNode.valueOf("firstRef")));

        final ObjectNode secondUidExt = mapper.createObjectNode();
        secondUidExt.set("optable", mapper.createObjectNode()
                .set("ref", TextNode.valueOf("secondRef")));

        final Eid firstEid = Eid.builder()
                .source("id5-sync.com")
                .inserter("optable.co")
                .uids(List.of(Uid.builder().id("id1").ext(firstUidExt).build()))
                .build();
        final Eid secondEid = Eid.builder()
                .source("id5-sync.com")
                .inserter("optable.co")
                .uids(List.of(Uid.builder().id("id2").ext(secondUidExt).build()))
                .build();
        final ObjectNode refs = mapper.createObjectNode();
        refs.set("secondRef", mapper.createObjectNode()
                .set("signature", TextNode.valueOf("secondSignature")));
        final TargetingResult targetingResult = new TargetingResult(
                List.of(),
                new Ortb2(new User(List.of(firstEid, secondEid), null)),
                refs);

        // when
        final String result = Id5Resolver.resolveId5Signature(targetingResult);

        // then
        assertThat(result).isEqualTo("secondSignature");
    }

    @Test
    public void shouldReturnNullWhenRefEntrySignatureIsBlank() {
        // given
        final ObjectNode uidExt = mapper.createObjectNode();
        uidExt.set("optable", mapper.createObjectNode()
                .set("ref", TextNode.valueOf("refValue")));

        final Eid eid = Eid.builder()
                .source("id5-sync.com")
                .inserter("optable.co")
                .uids(List.of(Uid.builder().id("id").ext(uidExt).build()))
                .build();
        final ObjectNode refs = mapper.createObjectNode();
        refs.set("refValue", mapper.createObjectNode()
                .set("signature", TextNode.valueOf("   ")));
        final TargetingResult targetingResult = new TargetingResult(
                List.of(),
                new Ortb2(new User(List.of(eid), null)),
                refs);

        // when
        final String result = Id5Resolver.resolveId5Signature(targetingResult);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void shouldReturnNullWhenRefEntrySignatureIsContainerNode() {
        // given
        final ObjectNode uidExt = mapper.createObjectNode();
        uidExt.set("optable", mapper.createObjectNode()
                .set("ref", TextNode.valueOf("refValue")));

        final Eid eid = Eid.builder()
                .source("id5-sync.com")
                .inserter("optable.co")
                .uids(List.of(Uid.builder().id("id").ext(uidExt).build()))
                .build();
        final ObjectNode refs = mapper.createObjectNode();
        refs.set("refValue", mapper.createObjectNode()
                .set("signature", mapper.createObjectNode()));
        final TargetingResult targetingResult = new TargetingResult(
                List.of(),
                new Ortb2(new User(List.of(eid), null)),
                refs);

        // when
        final String result = Id5Resolver.resolveId5Signature(targetingResult);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void shouldReturnNullWhenRefEntryHasNoSignature() {
        // given
        final ObjectNode uidExt = mapper.createObjectNode();
        uidExt.set("optable", mapper.createObjectNode()
                .set("ref", TextNode.valueOf("refValue")));

        final Eid eid = Eid.builder()
                .source("id5-sync.com")
                .inserter("optable.co")
                .uids(List.of(Uid.builder().id("id").ext(uidExt).build()))
                .build();
        final ObjectNode refs = mapper.createObjectNode();
        refs.set("refValue", mapper.createObjectNode()
                .set("other", TextNode.valueOf("value")));
        final TargetingResult targetingResult = new TargetingResult(
                List.of(),
                new Ortb2(new User(List.of(eid), null)),
                refs);

        // when
        final String result = Id5Resolver.resolveId5Signature(targetingResult);

        // then
        assertThat(result).isNull();
    }
}
