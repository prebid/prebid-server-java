package org.prebid.server.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.BrandVersion;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.request.SupplyChainNode;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.DataObject;
import com.iab.openrtb.response.ImageObject;
import com.iab.openrtb.response.Link;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class OpenRtbModelInheritanceTest {

    private final ObjectMapper mapper = ObjectMapperProvider.mapper();

    @Test
    void requestSubclassShouldRetainFieldsAndJsonRepresentation() throws Exception {
        // given
        final BidRequest source = BidRequest.builder()
                .id("request-id")
                .tmax(100L)
                .imp(Collections.singletonList(Imp.builder().id("imp-id").build()))
                .build();

        // when
        final BidRequest result = new ApplicationBidRequest(source);

        // then
        assertThat(result).isEqualTo(source);
        assertThat(source).isEqualTo(result);
        assertThat(result.hashCode()).isEqualTo(source.hashCode());
        assertThat(result.getImp()).isSameAs(source.getImp());
        assertThat(mapper.readTree(mapper.writeValueAsString(result)))
                .isEqualTo(mapper.readTree(mapper.writeValueAsString(source)));
        assertThat(mapper.readValue(mapper.writeValueAsString(result), BidRequest.class)).isEqualTo(source);
        assertThat(result.toBuilder().tmax(50L).build()).isEqualTo(source.toBuilder().tmax(50L).build());
        assertThat(result.getTmax()).isEqualTo(100L);
    }

    @Test
    void responseSubclassShouldRetainFieldsAndJsonRepresentation() throws Exception {
        // given
        final BidResponse source = BidResponse.builder()
                .id("request-id")
                .cur("USD")
                .seatbid(Collections.singletonList(SeatBid.builder()
                        .bid(Collections.singletonList(Bid.builder().id("bid-id").impid("imp-id").build()))
                        .build()))
                .build();

        // when
        final BidResponse result = new ApplicationBidResponse(source);

        // then
        assertThat(result).isEqualTo(source);
        assertThat(source).isEqualTo(result);
        assertThat(result.hashCode()).isEqualTo(source.hashCode());
        assertThat(result.getSeatbid()).isSameAs(source.getSeatbid());
        assertThat(mapper.readTree(mapper.writeValueAsString(result)))
                .isEqualTo(mapper.readTree(mapper.writeValueAsString(source)));
        assertThat(mapper.readValue(mapper.writeValueAsString(result), BidResponse.class)).isEqualTo(source);
        assertThat(result.toBuilder().cur("EUR").build()).isEqualTo(source.toBuilder().cur("EUR").build());
        assertThat(result.getCur()).isEqualTo("USD");
    }

    @Test
    void supplyChainFactoryShouldPreserveFieldsAndJsonRoundTrip() throws Exception {
        // given
        final SupplyChainNode node = SupplyChainNode.of(
                "ssp.test", "seller-id", "request-id", "seller", "seller.test", 1, mapper.createObjectNode());

        // when
        final SupplyChain result = SupplyChain.of(1, Collections.singletonList(node), "1.0", mapper.createObjectNode());

        // then
        assertThat(result.getComplete()).isEqualTo(1);
        assertThat(result.getNodes()).containsExactly(node);
        assertThat(result.getVer()).isEqualTo("1.0");
        assertThat(result.getExt()).isEqualTo(mapper.createObjectNode());
        assertThat(node.getAsi()).isEqualTo("ssp.test");
        assertThat(node.getSid()).isEqualTo("seller-id");
        assertThat(node.getRid()).isEqualTo("request-id");
        assertThat(node.getName()).isEqualTo("seller");
        assertThat(node.getDomain()).isEqualTo("seller.test");
        assertThat(node.getHp()).isEqualTo(1);
        assertThat(node.getExt()).isEqualTo(mapper.createObjectNode());
        assertThat(mapper.readValue(mapper.writeValueAsString(result), SupplyChain.class)).isEqualTo(result);
    }

    @Test
    void linkSubclassShouldBeCompatibleWithFactoryAndJsonRoundTrip() throws Exception {
        // given
        final Link source = Link.of("https://landing.test", Collections.singletonList("https://tracker.test"),
                "https://fallback.test", mapper.createObjectNode());

        // when
        final Link result = new Link(
                source.getUrl(), source.getClicktrackers(), source.getFallback(), source.getExt()) {
        };

        // then
        assertThat(result.getUrl()).isEqualTo("https://landing.test");
        assertThat(result.getClicktrackers()).containsExactly("https://tracker.test");
        assertThat(result.getFallback()).isEqualTo("https://fallback.test");
        assertThat(result.getExt()).isEqualTo(mapper.createObjectNode());
        assertThat(result).isEqualTo(source);
        assertThat(source).isEqualTo(result);
        assertThat(mapper.readValue(mapper.writeValueAsString(result), Link.class)).isEqualTo(source);
    }

    @Test
    void brandVersionShouldRetainPublicConstructor() throws Exception {
        // when
        final BrandVersion result = new BrandVersion("browser", Collections.singletonList("1"), null);

        // then
        assertThat(result.getBrand()).isEqualTo("browser");
        assertThat(result.getVersion()).containsExactly("1");
        assertThat(mapper.readValue(mapper.writeValueAsString(result), BrandVersion.class)).isEqualTo(result);
    }

    @Test
    void dataObjectSubclassShouldBeCompatibleWithBuilderAndJsonRoundTrip() throws Exception {
        // given
        final DataObject source = DataObject.builder()
                .type(1).len(5).value("value").ext(mapper.createObjectNode()).build();

        // when
        final DataObject result = new DataObject(
                source.getType(), source.getLen(), source.getValue(), source.getExt()) {
        };

        // then
        assertThat(result).isEqualTo(source);
        assertThat(source).isEqualTo(result);
        assertThat(mapper.readValue(mapper.writeValueAsString(result), DataObject.class)).isEqualTo(source);
        result.setValue("updated");
        assertThat(result.getValue()).isEqualTo("updated");
        assertThat(source.getValue()).isEqualTo("value");
    }

    @Test
    void imageObjectSubclassShouldBeCompatibleWithBuilderAndJsonRoundTrip() throws Exception {
        // given
        final ImageObject source = ImageObject.builder()
                .type(1).url("https://image.test").w(100).h(50).ext(mapper.createObjectNode()).build();

        // when
        final ImageObject result = new ImageObject(
                source.getType(), source.getUrl(), source.getW(), source.getH(), source.getExt()) {
        };

        // then
        assertThat(result).isEqualTo(source);
        assertThat(source).isEqualTo(result);
        assertThat(mapper.readValue(mapper.writeValueAsString(result), ImageObject.class)).isEqualTo(source);
        result.setW(200);
        assertThat(result.getW()).isEqualTo(200);
        assertThat(source.getW()).isEqualTo(100);
    }

    private static class ApplicationBidRequest extends BidRequest {

        private ApplicationBidRequest(BidRequest source) {
            super(
                    source.getId(),
                    source.getImp(),
                    source.getSite(),
                    source.getApp(),
                    source.getDooh(),
                    source.getDevice(),
                    source.getUser(),
                    source.getTest(),
                    source.getAt(),
                    source.getTmax(),
                    source.getWseat(),
                    source.getBseat(),
                    source.getAllimps(),
                    source.getCur(),
                    source.getWlang(),
                    source.getWlangb(),
                    source.getAcat(),
                    source.getBcat(),
                    source.getCattax(),
                    source.getBadv(),
                    source.getBapp(),
                    source.getSource(),
                    source.getRegs(),
                    source.getExt());
        }
    }

    private static class ApplicationBidResponse extends BidResponse {

        private ApplicationBidResponse(BidResponse source) {
            super(
                    source.getId(),
                    source.getSeatbid(),
                    source.getBidid(),
                    source.getCur(),
                    source.getCustomdata(),
                    source.getNbr(),
                    source.getExt());
        }
    }
}
