package org.prebid.server.util;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.SiteFpd;
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

    @Test
    public void mergeFamiliarShouldReturnMergedObject() {
        // given
        final Site site = Site.builder().id("testId").page("shouldNotBe").build();

        final SiteFpd siteFpd = SiteFpd.builder().domain("testDomain").page("testPage").build();

        // when
        final Site result = target.mergeFamiliar(siteFpd, site, Site.class);

        // then
        final Site mergedSite = Site.builder().id("testId").page("testPage").domain("testDomain").build();
        assertThat(result).isEqualTo(mergedSite);
    }

    @Test
    public void mergeJsonsShouldReturnValidInCaseOfNull() {
        // given
        final ObjectNode objectNode = mapper.createObjectNode();
        objectNode.put("test", "test");

        // when and then
        assertThat(target.mergeJsons(null, objectNode)).isEqualTo(objectNode);
        assertThat(target.mergeJsons(objectNode, null)).isEqualTo(objectNode);
        assertThat(target.mergeJsons(null, null)).isEqualTo(null);
    }

    @Test
    public void mergeJsonsShouldReturnMergedObject() {
        // given
        final ObjectNode originalObject = mapper.createObjectNode()
                .put("tagsId", 1357)
                .put("parametro", "")
                .put("device", "celular");

        final ObjectNode mergingObject = mapper.createObjectNode()
                .put("device", "ignored")
                .put("test", 1);

        // when
        final ObjectNode result = (ObjectNode) target.mergeJsons(originalObject, mergingObject);

        // then
        final ObjectNode mergedObject = mapper.createObjectNode()
                .put("test", 1)
                .put("tagsId", 1357)
                .put("parametro", "")
                .put("device", "celular");
        assertThat(result).isEqualTo(mergedObject);
    }
}
