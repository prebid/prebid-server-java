package org.prebid.server.util;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfigFpd;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonMergeUtilTest extends VertxTest {

    private JsonMergeUtil target;

    @Before
    public void setUp() {
        target = new JsonMergeUtil(jacksonMapper);
    }

    @Test
    public void encodeUrlShouldReturnExpectedValue() {
        // given
        final Site siteWithPage = Site.builder().page("testPage").build();
        final Publisher publisherWithId = Publisher.builder().id("testId").build();
        final App appWithPublisherId = App.builder().publisher(publisherWithId).build();
        final ExtBidderConfigFpd firstBidderConfigFpd = ExtBidderConfigFpd.of(siteWithPage, appWithPublisherId, null);

        final Site siteWithDomain = Site.builder().domain("testDomain").build();
        final Publisher publisherWithIdAndDomain = Publisher.builder().id("shouldNotBe").domain("domain").build();
        final App appWithUpdatedPublisher = App.builder().publisher(publisherWithIdAndDomain).build();
        final ExtBidderConfigFpd secondBidderConfigFpd = ExtBidderConfigFpd.of(siteWithDomain, appWithUpdatedPublisher,
                null);

        // when
        final ExtBidderConfigFpd result = target.merge(firstBidderConfigFpd, secondBidderConfigFpd,
                ExtBidderConfigFpd.class);

        // then
        final Site mergedSite = Site.builder().page("testPage").domain("testDomain").build();
        final Publisher mergedPublisher = Publisher.builder().id("testId").domain("domain").build();
        final App mergedApp = App.builder().publisher(mergedPublisher).build();
        final ExtBidderConfigFpd mergedConfigFpd = ExtBidderConfigFpd.of(mergedSite, mergedApp, null);

        assertThat(result).isEqualTo(mergedConfigFpd);
    }

}
