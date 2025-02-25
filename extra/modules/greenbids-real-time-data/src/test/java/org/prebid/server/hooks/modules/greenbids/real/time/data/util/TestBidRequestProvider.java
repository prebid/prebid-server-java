package org.prebid.server.hooks.modules.greenbids.real.time.data.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import org.prebid.server.json.ObjectMapperProvider;

import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

public class TestBidRequestProvider {

    public static final ObjectMapper MAPPER = ObjectMapperProvider.mapper();

    private TestBidRequestProvider() { }

    public static BidRequest givenBidRequest(UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
                                             List<Imp> imps) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .id("request")
                .imp(imps)
                        .site(givenSite())
                        .device(givenDevice()))
                .build();
    }

    public static Site givenSite() {
        return Site.builder().domain("www.leparisien.fr").build();
    }

    public static ObjectNode givenImpExt() {
        return givenImpExt(getRubiconNode(), getAppnexusNode(), getPubmaticNode());
    }

    public static ObjectNode givenImpExt(ObjectNode rubiconNode, ObjectNode appnexusNode, ObjectNode pubmaticNode) {
        final ObjectNode bidderNode = MAPPER.createObjectNode();

        if (rubiconNode != null) {
            bidderNode.set("rubicon", rubiconNode);
        }

        if (appnexusNode != null) {
            bidderNode.set("appnexus", appnexusNode);
        }

        if (pubmaticNode != null) {
            bidderNode.set("pubmatic", pubmaticNode);
        }

        return MAPPER.createObjectNode()
                .put("tid", "67eaab5f-27a6-4689-93f7-bd8f024576e3")
                .set("prebid", MAPPER.createObjectNode().set("bidder", bidderNode));
    }

    public static ObjectNode getPubmaticNode() {
        return MAPPER.createObjectNode()
                .put("publisherId", "156209")
                .put("adSlot", "slot1@300x250");
    }

    public static ObjectNode getAppnexusNode() {
        return MAPPER.createObjectNode().put("placementId", 123456);
    }

    public static ObjectNode getRubiconNode() {
        return MAPPER.createObjectNode()
                .put("accountId", 1001)
                .put("siteId", 267318)
                .put("zoneId", 1861698);
    }

    public static Device givenDevice(String countryAlpha3) {
        final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/59.0.3071.115 Safari/537.36";
        final Geo geo = Geo.builder().country(countryAlpha3).build();
        return Device.builder().ua(userAgent).ip("151.101.194.216").geo(geo).build();
    }

    public static Device givenDevice() {
        final String userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36"
                + " (KHTML, like Gecko) Chrome/59.0.3071.115 Safari/537.36";
        return Device.builder().ua(userAgent).ip("151.101.194.216").build();
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
