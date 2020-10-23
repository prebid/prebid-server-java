package org.prebid.server.util;

import com.fasterxml.jackson.databind.node.ObjectNode;
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
    public void mergeShouldReturnMergedObject() {
        // given
        final ObjectNode siteWithPage = mapper.valueToTree(Site.builder().page("testPage").build());
        final Publisher publisherWithId = Publisher.builder().id("testId").build();
        final ObjectNode appWithPublisherId = mapper.valueToTree(App.builder().publisher(publisherWithId).build());
        final ExtBidderConfigFpd firstBidderConfigFpd = ExtBidderConfigFpd.of(siteWithPage, appWithPublisherId, null);

        final ObjectNode siteWithDomain = mapper.valueToTree(Site.builder().domain("testDomain").build());
        final Publisher publisherWithIdAndDomain = Publisher.builder().id("shouldNotBe").domain("domain").build();
        final ObjectNode appWithUpdatedPublisher = mapper.valueToTree(App.builder()
                .publisher(publisherWithIdAndDomain).build());
        final ExtBidderConfigFpd secondBidderConfigFpd = ExtBidderConfigFpd.of(siteWithDomain, appWithUpdatedPublisher,
                null);

        // when
        final ExtBidderConfigFpd result = target.merge(firstBidderConfigFpd, secondBidderConfigFpd,
                ExtBidderConfigFpd.class);

        // then
        final ObjectNode mergedSite = mapper.valueToTree(Site.builder().page("testPage").domain("testDomain").build());
        final Publisher mergedPublisher = Publisher.builder().id("testId").domain("domain").build();
        final ObjectNode mergedApp = mapper.valueToTree(App.builder().publisher(mergedPublisher).build());
        final ExtBidderConfigFpd mergedConfigFpd = ExtBidderConfigFpd.of(mergedSite, mergedApp, null);

        assertThat(result).isEqualTo(mergedConfigFpd);
    }

    @Test
    public void mergeShouldReturnOriginalObjectWhenMergedObjectIsNull() {
        // given
        final Site site = Site.builder().build();

        // when
        final Site result = target.merge(site, null, Site.class);

        // then
        assertThat(result).isEqualTo(site);
    }

    @Test
    public void mergeShouldReturnMergedObjectWhenOriginalObjectIsNull() {
        // given
        final Site site = Site.builder().build();

        // when
        final Site result = target.merge(null, site, Site.class);

        // then
        assertThat(result).isEqualTo(site);
    }

}
