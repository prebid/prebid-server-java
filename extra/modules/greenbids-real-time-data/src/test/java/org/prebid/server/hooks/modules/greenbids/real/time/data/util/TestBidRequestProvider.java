package org.prebid.server.hooks.modules.greenbids.real.time.data.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;

import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

public class TestBidRequestProvider {

    public static final ObjectMapper MAPPER = ObjectMapperProvider.mapper();

    private TestBidRequestProvider() { }

    public static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            List<Imp> imps,
            Device device) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .id("request")
                .imp(imps)
                .site(givenSite(site -> site))
                .device(device)).build();
    }

    public static BidRequest givenBidRequestWithExtension(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            List<Imp> imps) {
        final BidRequest.BidRequestBuilder bidRequestBuilder = BidRequest.builder()
                .id("request")
                .imp(imps)
                .site(givenSite(site -> site))
                .device(givenDevice(device -> device))
                .ext(givenExtRequest());

        return bidRequestCustomizer.apply(bidRequestBuilder).build();
    }

    public static ExtRequest givenExtRequest() {
        final ObjectNode greenbidsNode = new ObjectMapper().createObjectNode();
        greenbidsNode.put("pbuid", "leparisien");
        greenbidsNode.put("greenbidsSampling", 1.0);

        final ObjectNode analyticsNode = new ObjectMapper().createObjectNode();
        analyticsNode.set("greenbids", greenbidsNode);

        return ExtRequest.of(
                ExtRequestPrebid
                        .builder()
                        .analytics(analyticsNode)
                        .build());
    }

    public static Site givenSite(UnaryOperator<Site.SiteBuilder> siteCustomizer) {
        return siteCustomizer.apply(Site.builder().domain("www.leparisien.fr")).build();
    }

    public static ObjectNode givenImpExt() {
        final ObjectNode bidderNode = MAPPER.createObjectNode();

        final ObjectNode rubiconNode = MAPPER.createObjectNode();
        rubiconNode.put("accountId", 1001);
        rubiconNode.put("siteId", 267318);
        rubiconNode.put("zoneId", 1861698);
        bidderNode.set("rubicon", rubiconNode);

        final ObjectNode appnexusNode = MAPPER.createObjectNode();
        appnexusNode.put("placementId", 123456);
        bidderNode.set("appnexus", appnexusNode);

        final ObjectNode pubmaticNode = MAPPER.createObjectNode();
        pubmaticNode.put("publisherId", "156209");
        pubmaticNode.put("adSlot", "slot1@300x250");
        bidderNode.set("pubmatic", pubmaticNode);

        final ObjectNode prebidNode = MAPPER.createObjectNode();
        prebidNode.set("bidder", bidderNode);

        final ObjectNode extNode = MAPPER.createObjectNode();
        extNode.set("prebid", prebidNode);
        extNode.set("tid", TextNode.valueOf("67eaab5f-27a6-4689-93f7-bd8f024576e3"));

        return extNode;
    }

    public static ObjectNode givenImpExtToFilterAllBidders() {
        final ObjectNode bidderNode = MAPPER.createObjectNode();

        final ObjectNode appnexusNode = MAPPER.createObjectNode();
        appnexusNode.put("placementId", 789);
        bidderNode.set("appnexus", appnexusNode);

        final ObjectNode prebidNode = MAPPER.createObjectNode();
        prebidNode.set("bidder", bidderNode);

        final ObjectNode extNode = MAPPER.createObjectNode();
        extNode.set("prebid", prebidNode);
        extNode.set("tid", TextNode.valueOf("af65045c-2774-44c2-a949-4f42d5c9e179"));

        return extNode;
    }

    public static Device givenDevice(UnaryOperator<Device.DeviceBuilder> deviceCustomizer, String countryAlpha3) {
        final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/59.0.3071.115 Safari/537.36";
        final Geo geo = givenGeoWithCountry(countryAlpha3);
        return deviceCustomizer.apply(Device.builder().ua(userAgent).ip("151.101.194.216").geo(geo)).build();
    }

    public static Device givenDevice(UnaryOperator<Device.DeviceBuilder> deviceCustomizer) {
        final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/59.0.3071.115 Safari/537.36";
        return deviceCustomizer.apply(Device.builder().ua(userAgent).ip("151.101.194.216")).build();
    }

    public static Geo givenGeoWithCountry(String countryAlpha3) {
        return Geo.builder().country(countryAlpha3).build();
    }

    public static Device givenDeviceWithoutUserAgent(UnaryOperator<Device.DeviceBuilder> deviceCustomizer) {
        return deviceCustomizer.apply(Device.builder().ip("151.101.194.216")).build();
    }

    public static Banner givenBanner() {
        final Format format = Format.builder()
                .w(320)
                .h(50)
                .build();

        return Banner.builder()
                .format(Collections.singletonList(format))
                .w(240)
                .h(400)
                .build();
    }
}
