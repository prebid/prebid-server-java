package org.prebid.server.hooks.modules.greenbids.real.time.data.v1;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;

import java.util.List;
import java.util.function.UnaryOperator;

public class TestBidRequestProvider {

    private final JacksonMapper jacksonMapper;

    public TestBidRequestProvider(JacksonMapper jacksonMapper) {
        this.jacksonMapper = jacksonMapper;
    }

    public BidRequest givenBidRequest(
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

    public Site givenSite(UnaryOperator<Site.SiteBuilder> siteCustomizer) {
        return siteCustomizer.apply(Site.builder().domain("www.leparisien.fr")).build();
    }

    public ObjectNode givenImpExt() {
        final ObjectNode bidderNode = jacksonMapper.mapper().createObjectNode();

        final ObjectNode rubiconNode = jacksonMapper.mapper().createObjectNode();
        rubiconNode.put("accountId", 1001);
        rubiconNode.put("siteId", 267318);
        rubiconNode.put("zoneId", 1861698);
        bidderNode.set("rubicon", rubiconNode);

        final ObjectNode appnexusNode = jacksonMapper.mapper().createObjectNode();
        appnexusNode.put("placementId", 123456);
        bidderNode.set("appnexus", appnexusNode);

        final ObjectNode pubmaticNode = jacksonMapper.mapper().createObjectNode();
        pubmaticNode.put("publisherId", "156209");
        pubmaticNode.put("adSlot", "slot1@300x250");
        bidderNode.set("pubmatic", pubmaticNode);

        final ObjectNode prebidNode = jacksonMapper.mapper().createObjectNode();
        prebidNode.set("bidder", bidderNode);

        final ObjectNode extNode = jacksonMapper.mapper().createObjectNode();
        extNode.set("prebid", prebidNode);
        extNode.set("tid", null);

        return extNode;
    }
}
