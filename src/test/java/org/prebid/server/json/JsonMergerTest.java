package org.prebid.server.json;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfigOrtb;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonMergerTest extends VertxTest {

    private JsonMerger target;

    @BeforeEach
    public void setUp() {
        target = new JsonMerger(jacksonMapper);
    }

    @Test
    public void mergeShouldReturnMergedObject() {
        // given
        final ObjectNode siteWithPage = mapper.valueToTree(Site.builder().page("testPage").build());
        final Publisher publisherWithId = Publisher.builder().id("testId").build();
        final ObjectNode appWithPublisherId = mapper.valueToTree(App.builder().publisher(publisherWithId).build());
        final ObjectNode doohWithVenueType = mapper.valueToTree(Dooh.builder().venuetype(List.of("venuetype")).build());
        final ObjectNode deviceWithMakeAndModel = mapper.valueToTree(Device.builder()
                .make("TestMake").model("TestModel").build());
        final ExtBidderConfigOrtb firstBidderConfigFpd = ExtBidderConfigOrtb.of(
                siteWithPage,
                appWithPublisherId,
                doohWithVenueType,
                null,
                deviceWithMakeAndModel);

        final ObjectNode siteWithDomain = mapper.valueToTree(Site.builder().domain("testDomain").build());
        final Publisher publisherWithIdAndDomain = Publisher.builder().id("shouldNotBe").domain("domain").build();
        final ObjectNode appWithUpdatedPublisher = mapper.valueToTree(App.builder()
                .publisher(publisherWithIdAndDomain).build());
        final ObjectNode doohWithVenueTypeTax = mapper.valueToTree(Dooh.builder().venuetypetax(3).build());
        final ObjectNode deviceWithDeviceType = mapper.valueToTree(Device.builder().devicetype(6).build());

        final ExtBidderConfigOrtb secondBidderConfigFpd =
                ExtBidderConfigOrtb.of(siteWithDomain, appWithUpdatedPublisher, doohWithVenueTypeTax,
                        null, deviceWithDeviceType);

        // when
        final ExtBidderConfigOrtb result = target.merge(
                firstBidderConfigFpd,
                secondBidderConfigFpd,
                ExtBidderConfigOrtb.class);

        // then
        final ObjectNode mergedSite = mapper.valueToTree(Site.builder().page("testPage").domain("testDomain").build());
        final Publisher mergedPublisher = Publisher.builder().id("testId").domain("domain").build();
        final ObjectNode mergedApp = mapper.valueToTree(App.builder().publisher(mergedPublisher).build());
        final ObjectNode mergedDooh = mapper.valueToTree(
                Dooh.builder().venuetype(List.of("venuetype")).venuetypetax(3).build());
        final ObjectNode mergedDevice = mapper.valueToTree(Device.builder().make("TestMake").model("TestModel")
                .devicetype(6).build());
        final ExtBidderConfigOrtb mergedConfigFpd = ExtBidderConfigOrtb.of(mergedSite, mergedApp, mergedDooh,
                null, mergedDevice);

        assertThat(result).isEqualTo(mergedConfigFpd);
    }

    @Test
    public void mergeShouldReturnOriginalObjectWhenMergedObjectIsNull() {
        // given
        final Site site = Site.builder().build();
        final Device device = Device.builder().build();

        // when
        final Site result = target.merge(site, null, Site.class);
        final Device resultDevice = target.merge(device, null, Device.class);

        // then
        assertThat(result).isEqualTo(site);
        assertThat(resultDevice).isEqualTo(device);
    }

    @Test
    public void mergeShouldReturnMergedObjectWhenOriginalObjectIsNull() {
        // given
        final Site site = Site.builder().build();
        final Device device = Device.builder().build();

        // when
        final Site result = target.merge(null, site, Site.class);
        final Device resultDevice = target.merge(null, device, Device.class);

        // then
        assertThat(result).isEqualTo(site);
        assertThat(resultDevice).isEqualTo(device);
    }

}
