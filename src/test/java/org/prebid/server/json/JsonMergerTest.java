package org.prebid.server.json;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfigOrtb;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonMergerTest extends VertxTest {

    private JsonMerger target;

    @Before
    public void setUp() {
        target = new JsonMerger(jacksonMapper);
    }

    @Test
    public void mergeShouldReturnMergedObject() {
        // given
        final ObjectNode siteWithPage = mapper.valueToTree(Site.builder().page("testPage").build());
        final Publisher publisherWithId = Publisher.builder().id("testId").build();
        final ObjectNode appWithPublisherId = mapper.valueToTree(App.builder().publisher(publisherWithId).build());
        final ExtBidderConfigOrtb firstBidderConfigFpd = ExtBidderConfigOrtb.of(siteWithPage, appWithPublisherId, null);

        final ObjectNode siteWithDomain = mapper.valueToTree(Site.builder().domain("testDomain").build());
        final Publisher publisherWithIdAndDomain = Publisher.builder().id("shouldNotBe").domain("domain").build();
        final ObjectNode appWithUpdatedPublisher = mapper.valueToTree(App.builder()
                .publisher(publisherWithIdAndDomain).build());
        final ExtBidderConfigOrtb secondBidderConfigFpd =
                ExtBidderConfigOrtb.of(siteWithDomain, appWithUpdatedPublisher, null);

        // when
        final ExtBidderConfigOrtb result = target.merge(firstBidderConfigFpd, secondBidderConfigFpd,
                ExtBidderConfigOrtb.class);

        // then
        final ObjectNode mergedSite = mapper.valueToTree(Site.builder().page("testPage").domain("testDomain").build());
        final Publisher mergedPublisher = Publisher.builder().id("testId").domain("domain").build();
        final ObjectNode mergedApp = mapper.valueToTree(App.builder().publisher(mergedPublisher).build());
        final ExtBidderConfigOrtb mergedConfigFpd = ExtBidderConfigOrtb.of(mergedSite, mergedApp, null);

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
