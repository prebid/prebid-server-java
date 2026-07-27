package org.prebid.server.auction;

import com.iab.openrtb.request.Eid;
import org.junit.jupiter.api.Test;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidDataEidPermissions;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class EidPermissionResolverTest {

    @Test
    public void resolveShouldFilterEidsWhenBidderIsNotAllowedForSourceIgnoringCase() {
        // given
        final List<Eid> userEids = asList(
                Eid.builder().source("source1").build(),
                Eid.builder().source("source2").build());

        final EidPermissionResolver resolver = EidPermissionResolver.of(
                singletonList(ExtRequestPrebidDataEidPermissions.builder()
                        .source("source1")
                        .bidders(singletonList("OtHeRbIdDeR"))
                        .build()));

        // when and then
        assertThat(resolver.resolveAllowedEids(userEids, "someBidder"))
                .containsExactly(Eid.builder().source("source2").build());
    }

    @Test
    void resolveShouldFilterEidsWhenBidderIsNotAllowedForInserterIgnoringCase() {
        // given
        final List<Eid> userEids = asList(
                Eid.builder().inserter("inserter1").build(),
                Eid.builder().inserter("inserter2").build());

        final EidPermissionResolver resolver = EidPermissionResolver.of(
                singletonList(ExtRequestPrebidDataEidPermissions.builder()
                        .inserter("inserter1")
                        .bidders(singletonList("OtHeRbIdDeR"))
                        .build()));

        // when and then
        assertThat(resolver.resolveAllowedEids(userEids, "someBidder"))
                .containsExactly(Eid.builder().inserter("inserter2").build());
    }

    @Test
    public void resolveShouldFilterEidsWhenBidderIsNotAllowedForMatcherIgnoringCase() {
        // given
        final List<Eid> userEids = asList(
                Eid.builder().matcher("matcher1").build(),
                Eid.builder().matcher("matcher2").build());

        final EidPermissionResolver resolver = EidPermissionResolver.of(
                singletonList(ExtRequestPrebidDataEidPermissions.builder()
                        .matcher("matcher1")
                        .bidders(singletonList("OtHeRbIdDeR"))
                        .build()));

        // when and then
        assertThat(resolver.resolveAllowedEids(userEids, "someBidder"))
                .containsExactly(Eid.builder().matcher("matcher2").build());
    }

    @Test
    public void resolveShouldFilterEidsWhenBidderIsNotAllowedForMm() {
        // given
        final List<Eid> userEids = asList(Eid.builder().mm(1).build(), Eid.builder().mm(2).build());

        final EidPermissionResolver resolver = EidPermissionResolver.of(
                singletonList(ExtRequestPrebidDataEidPermissions.builder()
                        .mm(1)
                        .bidders(singletonList("OtHeRbIdDeR"))
                        .build()));

        // when and then
        assertThat(resolver.resolveAllowedEids(userEids, "someBidder"))
                .containsExactly(Eid.builder().mm(2).build());
    }

    @Test
    public void resolveShouldFilterEidsWhenBidderIsNotAllowedUsingMultipleCriteria() {
        // given
        final List<Eid> userEids = asList(
                Eid.builder().inserter("inserter1").source("source1").matcher("matcher1").mm(1).build(),
                Eid.builder().inserter("inserter2").source("source2").matcher("matcher2").mm(2).build());

        final EidPermissionResolver resolver = EidPermissionResolver.of(
                singletonList(ExtRequestPrebidDataEidPermissions.builder()
                        .inserter("inserter1")
                        .source("source1")
                        .matcher("matcher1")
                        .mm(1)
                        .bidders(singletonList("OtHeRbIdDeR"))
                        .build()));

        // when and then
        assertThat(resolver.resolveAllowedEids(userEids, "someBidder")).containsExactly(
                Eid.builder().inserter("inserter2").source("source2").matcher("matcher2").mm(2).build());
    }

    @Test
    public void resolveShouldFilterEidsWhenEveryCriteriaMatches() {
        // given
        final List<Eid> userEids = asList(
                Eid.builder().inserter("inserter1").source("source1").matcher("matcher1").mm(1).build(),
                Eid.builder().inserter("inserter2").source("source2").matcher("matcher2").mm(2).build());

        final EidPermissionResolver resolver = EidPermissionResolver.of(
                singletonList(ExtRequestPrebidDataEidPermissions.builder()
                        .inserter("inserter1")
                        .source("source2")
                        .matcher("matcher3")
                        .mm(4)
                        .bidders(singletonList("OtHeRbIdDeR"))
                        .build()));

        // when and then
        assertThat(resolver.resolveAllowedEids(userEids, "someBidder")).containsExactly(
                Eid.builder().inserter("inserter1").source("source1").matcher("matcher1").mm(1).build(),
                Eid.builder().inserter("inserter2").source("source2").matcher("matcher2").mm(2).build());
    }

    @Test
    public void resolveShouldFilterEidsWhenBidderIsNotAllowedUsingTheMostSpecificRule() {
        // given
        final List<Eid> userEids = asList(
                Eid.builder().inserter("inserter1").source("source1").matcher("matcher1").mm(1).build(),
                Eid.builder().inserter("inserter2").source("source2").matcher("matcher2").mm(2).build());

        final EidPermissionResolver resolver = EidPermissionResolver.of(
                asList(ExtRequestPrebidDataEidPermissions.builder()
                                .inserter("inserter1")
                                .bidders(singletonList("someBidder"))
                                .build(),
                        ExtRequestPrebidDataEidPermissions.builder()
                                .inserter("inserter1")
                                .source("source1")
                                .matcher("matcher1")
                                .mm(1)
                                .bidders(singletonList("OtHeRbIdDeR"))
                                .build()));

        // when and then
        assertThat(resolver.resolveAllowedEids(userEids, "someBidder")).containsExactly(
                Eid.builder().inserter("inserter2").source("source2").matcher("matcher2").mm(2).build());
    }

    @Test
    public void resolveShouldNotFilterUserExtEidsWhenBidderIsAllowedUsingTheMostSpecificRule() {
        // given
        final List<Eid> userEids = asList(
                Eid.builder().inserter("inserter1").source("source1").matcher("matcher1").mm(1).build(),
                Eid.builder().inserter("inserter2").source("source2").matcher("matcher2").mm(2).build());

        final EidPermissionResolver resolver = EidPermissionResolver.of(
                asList(ExtRequestPrebidDataEidPermissions.builder()
                                .inserter("inserter1")
                                .bidders(singletonList("OtHeRbIdDeR"))
                                .build(),
                        ExtRequestPrebidDataEidPermissions.builder()
                                .inserter("inserter1")
                                .source("source1")
                                .matcher("matcher1")
                                .mm(1)
                                .bidders(singletonList("someBidder"))
                                .build()));

        // when and then
        assertThat(resolver.resolveAllowedEids(userEids, "someBidder")).containsExactly(
                Eid.builder().inserter("inserter1").source("source1").matcher("matcher1").mm(1).build(),
                Eid.builder().inserter("inserter2").source("source2").matcher("matcher2").mm(2).build());
    }

    @Test
    public void resolveShouldNotFilterUserExtEidsWhenBidderIsAllowedUsingMultipleSameSpecificityRules() {
        // given
        final List<Eid> userEids = asList(
                Eid.builder().inserter("inserter1").source("source1").matcher("matcher1").mm(1).build(),
                Eid.builder().inserter("inserter2").source("source2").matcher("matcher2").mm(2).build());

        final EidPermissionResolver resolver = EidPermissionResolver.of(
                asList(ExtRequestPrebidDataEidPermissions.builder()
                                .inserter("inserter1")
                                .source("source1")
                                .bidders(singletonList("OtHeRbIdDeR"))
                                .build(),
                        ExtRequestPrebidDataEidPermissions.builder()
                                .matcher("matcher1")
                                .mm(1)
                                .bidders(singletonList("someBidder"))
                                .build()));

        // when and then
        assertThat(resolver.resolveAllowedEids(userEids, "someBidder")).containsExactly(
                Eid.builder().inserter("inserter1").source("source1").matcher("matcher1").mm(1).build(),
                Eid.builder().inserter("inserter2").source("source2").matcher("matcher2").mm(2).build());
    }

    @Test
    public void resolveShouldNotFilterEidsWhenEidsPermissionDoesNotContainSourceIgnoringCase() {
        // given
        final List<Eid> userEids = singletonList(Eid.builder().source("source1").build());

        final EidPermissionResolver resolver = EidPermissionResolver.of(
                singletonList(ExtRequestPrebidDataEidPermissions.builder()
                        .source("source2")
                        .bidders(singletonList("OtHeRbIdDeR"))
                        .build()));

        // when and then
        assertThat(resolver.resolveAllowedEids(userEids, "someBidder"))
                .containsExactly(Eid.builder().source("source1").build());
    }

    @Test
    public void resolveShouldNotFilterEidsWhenSourceAllowedForAllBiddersIgnoringCase() {
        // given
        final List<Eid> userEids = singletonList(Eid.builder().source("source1").build());

        final EidPermissionResolver resolver = EidPermissionResolver.of(
                singletonList(ExtRequestPrebidDataEidPermissions.builder()
                        .source("source1")
                        .bidders(singletonList("*"))
                        .build()));

        // when and then
        assertThat(resolver.resolveAllowedEids(userEids, "someBidder"))
                .containsExactly(Eid.builder().source("source1").build());
    }

    @Test
    public void resolveShouldNotFilterEidsWhenSourceAllowedForBidderIgnoringCase() {
        // given
        final List<Eid> userEids = singletonList(Eid.builder().source("source1").build());

        final EidPermissionResolver resolver = EidPermissionResolver.of(
                singletonList(ExtRequestPrebidDataEidPermissions.builder()
                        .source("source1")
                        .bidders(singletonList("SoMeBiDdEr"))
                        .build()));

        // when and then
        assertThat(resolver.resolveAllowedEids(userEids, "someBidder"))
                .containsExactly(Eid.builder().source("source1").build());
    }
}
