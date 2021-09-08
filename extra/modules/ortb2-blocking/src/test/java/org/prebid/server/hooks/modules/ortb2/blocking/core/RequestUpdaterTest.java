package org.prebid.server.hooks.modules.ortb2.blocking.core;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedAttributes;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

class RequestUpdaterTest {

    @Test
    public void shouldReplaceBadv() {
        // given
        final RequestUpdater updater = RequestUpdater.create(
            BlockedAttributes.builder().badv(asList("domain1.com", "domain2.com")).build());
        final BidRequest request = BidRequest.builder()
            .badv(singletonList("overriddendomain1.com"))
            .build();

        // when and then
        assertThat(updater.update(request)).isEqualTo(BidRequest.builder()
            .badv(asList("domain1.com", "domain2.com"))
            .build());
    }

    @Test
    public void shouldKeepBadvWhenNoBlockedBadv() {
        // given
        final RequestUpdater updater = RequestUpdater.create(
            BlockedAttributes.builder().build());
        final BidRequest request = BidRequest.builder()
            .badv(singletonList("domain1.com"))
            .build();

        // when and then
        assertThat(updater.update(request)).isEqualTo(BidRequest.builder()
            .badv(singletonList("domain1.com"))
            .build());
    }

    @Test
    public void shouldReplaceBcat() {
        // given
        final RequestUpdater updater = RequestUpdater.create(
            BlockedAttributes.builder().bcat(asList("cat1", "cat2")).build());
        final BidRequest request = BidRequest.builder()
            .bcat(singletonList("overriddencat1"))
            .build();

        // when and then
        assertThat(updater.update(request)).isEqualTo(BidRequest.builder()
            .bcat(asList("cat1", "cat2"))
            .build());
    }

    @Test
    public void shouldKeepBcatWhenNoBlockedBcat() {
        // given
        final RequestUpdater updater = RequestUpdater.create(
            BlockedAttributes.builder().build());
        final BidRequest request = BidRequest.builder()
            .bcat(singletonList("cat1"))
            .build();

        // when and then
        assertThat(updater.update(request)).isEqualTo(BidRequest.builder()
            .bcat(singletonList("cat1"))
            .build());
    }

    @Test
    public void shouldReplaceBapp() {
        // given
        final RequestUpdater updater = RequestUpdater.create(
            BlockedAttributes.builder().bapp(asList("app1", "app2")).build());
        final BidRequest request = BidRequest.builder()
            .bapp(singletonList("overriddenapp1"))
            .build();

        // when and then
        assertThat(updater.update(request)).isEqualTo(BidRequest.builder()
            .bapp(asList("app1", "app2"))
            .build());
    }

    @Test
    public void shouldKeepBappWhenNoBlockedBapp() {
        // given
        final RequestUpdater updater = RequestUpdater.create(
            BlockedAttributes.builder().build());
        final BidRequest request = BidRequest.builder()
            .bapp(singletonList("app1"))
            .build();

        // when and then
        assertThat(updater.update(request)).isEqualTo(BidRequest.builder()
            .bapp(singletonList("app1"))
            .build());
    }

    @Test
    public void shouldReplaceImpBtype() {
        // given
        final RequestUpdater updater = RequestUpdater.create(
            BlockedAttributes.builder().btype(singletonMap("impId1", asList(1, 2))).build());
        final BidRequest request = BidRequest.builder()
            .imp(singletonList(Imp.builder()
                .id("impId1")
                .banner(Banner.builder()
                    .btype(singletonList(123))
                    .build())
                .build()))
            .build();

        // when and then
        assertThat(updater.update(request)).isEqualTo(BidRequest.builder()
            .imp(singletonList(Imp.builder()
                .id("impId1")
                .banner(Banner.builder()
                    .btype(asList(1, 2))
                    .build())
                .build()))
            .build());
    }

    @Test
    public void shouldReplaceImpBattr() {
        // given
        final RequestUpdater updater = RequestUpdater.create(
            BlockedAttributes.builder().battr(singletonMap("impId1", asList(1, 2))).build());
        final BidRequest request = BidRequest.builder()
            .imp(singletonList(Imp.builder()
                .id("impId1")
                .banner(Banner.builder()
                    .battr(singletonList(123))
                    .build())
                .build()))
            .build();

        // when and then
        assertThat(updater.update(request)).isEqualTo(BidRequest.builder()
            .imp(singletonList(Imp.builder()
                .id("impId1")
                .banner(Banner.builder()
                    .battr(asList(1, 2))
                    .build())
                .build()))
            .build());
    }

    @Test
    public void shouldNotChangeImpsWhenNoBlockedBannerTypeAndBlockedBannerAttr() {
        // given
        final RequestUpdater updater = RequestUpdater.create(
            BlockedAttributes.builder().build());
        final List<Imp> imps = singletonList(Imp.builder().build());
        final BidRequest request = BidRequest.builder()
            .imp(imps)
            .build();

        // when and then
        assertThat(updater.update(request).getImp()).isSameAs(imps);
    }

    @Test
    public void shouldNotChangeImpWhenNoBlockedBannerTypeAndBlockedBannerAttrForImp() {
        // given
        final RequestUpdater updater = RequestUpdater.create(
            BlockedAttributes.builder()
                .btype(singletonMap("impId2", singletonList(1)))
                .battr(singletonMap("impId2", singletonList(1)))
                .build());
        final Imp imp = Imp.builder().build();
        final BidRequest request = BidRequest.builder()
            .imp(singletonList(imp))
            .build();

        // when and then
        final BidRequest updatedRequest = updater.update(request);
        assertThat(updatedRequest.getImp()).hasSize(1);
        assertThat(updatedRequest.getImp().get(0)).isSameAs(imp);
    }

    @Test
    public void shouldKeepImpBtypeWhenNoBlockedBannerTypeAndPresentBlockedBannerAttrForImp() {
        // given
        final RequestUpdater updater = RequestUpdater.create(
            BlockedAttributes.builder()
                .battr(singletonMap("impId1", singletonList(1)))
                .build());
        final Imp imp = Imp.builder()
            .id("impId1")
            .banner(Banner.builder().btype(singletonList(1000)).build())
            .build();
        final BidRequest request = BidRequest.builder()
            .imp(singletonList(imp))
            .build();

        // when and then
        assertThat(updater.update(request)).isEqualTo(BidRequest.builder()
            .imp(singletonList(Imp.builder()
                .id("impId1")
                .banner(Banner.builder()
                    .btype(singletonList(1000))
                    .battr(singletonList(1))
                    .build())
                .build()))
            .build());
    }

    @Test
    public void shouldKeepImpBattrWhenNoBlockedBannerAttrAndPresentBlockedBannerTypeForImp() {
        // given
        final RequestUpdater updater = RequestUpdater.create(
            BlockedAttributes.builder()
                .btype(singletonMap("impId1", singletonList(1)))
                .build());
        final Imp imp = Imp.builder()
            .id("impId1")
            .banner(Banner.builder().battr(singletonList(1000)).build())
            .build();
        final BidRequest request = BidRequest.builder()
            .imp(singletonList(imp))
            .build();

        // when and then
        assertThat(updater.update(request)).isEqualTo(BidRequest.builder()
            .imp(singletonList(Imp.builder()
                .id("impId1")
                .banner(Banner.builder()
                    .btype(singletonList(1))
                    .battr(singletonList(1000))
                    .build())
                .build()))
            .build());
    }

    @Test
    public void shouldUpdateAllAttributes() {
        // given
        final RequestUpdater updater = RequestUpdater.create(
            BlockedAttributes.builder()
                .badv(asList("domain1.com", "domain2.com"))
                .bcat(asList("cat1", "cat2"))
                .bapp(asList("app1", "app2"))
                .btype(singletonMap("impId1", asList(1, 2)))
                .battr(singletonMap("impId1", asList(1, 2)))
                .build());
        final BidRequest request = BidRequest.builder()
            .imp(singletonList(Imp.builder().id("impId1").build()))
            .build();

        // when and then
        assertThat(updater.update(request)).isEqualTo(BidRequest.builder()
            .badv(asList("domain1.com", "domain2.com"))
            .bcat(asList("cat1", "cat2"))
            .bapp(asList("app1", "app2"))
            .imp(singletonList(Imp.builder()
                .id("impId1")
                .banner(Banner.builder()
                    .btype(asList(1, 2))
                    .battr(asList(1, 2))
                    .build())
                .build()))
            .build());
    }
}
