package org.prebid.server.hooks.modules.greenbids.real.time.data.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;

import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

public class TestBidRequestProvider {

    public static final ObjectMapper MAPPER = ObjectMapperProvider.mapper();

    private TestBidRequestProvider() { }

    public static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            List<Imp> imps,
            Device device,
            ExtRequest extRequest) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .id("request")
                .imp(imps)
                .site(givenSite(site -> site))
                .device(device)
                .ext(extRequest)).build();
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

    public static Device givenDevice(UnaryOperator<Device.DeviceBuilder> deviceCustomizer) {
        final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/59.0.3071.115 Safari/537.36";
        return deviceCustomizer.apply(Device.builder().ua(userAgent).ip("151.101.194.216")).build();
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
