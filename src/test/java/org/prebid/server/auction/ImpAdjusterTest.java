package org.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.validation.ImpValidator;
import org.prebid.server.validation.ValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ImpAdjusterTest extends VertxTest {

    @Mock
    private ImpValidator impValidator;

    @Mock
    private BidderCatalog bidderCatalog;

    private ImpAdjuster target;

    private BidderAliases bidderAliases;

    @BeforeEach
    public void setUp() {
        target = new ImpAdjuster(jacksonMapper, new JsonMerger(jacksonMapper), impValidator);
        bidderAliases = BidderAliases.of(
                Map.of("someBidderAlias", "someBidder"), Collections.emptyMap(), bidderCatalog);
    }

    @Test
    public void adjustShouldReturnOriginalImpWhenImpExtPrebidImpIsNull() {
        // given
        final Imp givenImp = Imp.builder().build();
        final List<String> debugMessages = new ArrayList<>();

        // when
        final Imp result = target.adjust(givenImp, "someBidder", bidderAliases, debugMessages);

        // then
        assertThat(result).isSameAs(givenImp);
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void adjustShouldReturnOriginalImpWhenImpExtPrebidImpIsAbsent() {
        // given
        final Imp givenImp = Imp.builder()
                .ext(mapper.createObjectNode().set("prebid", mapper.createObjectNode()))
                .build();
        final List<String> debugMessages = new ArrayList<>();

        // when
        final Imp result = target.adjust(givenImp, "someBidder", bidderAliases, debugMessages);

        // then
        assertThat(result).isSameAs(givenImp);
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void adjustShouldSetImpExtIgsAeWhenImpExtAeIsZero() {
        // given
        final ObjectNode ext = mapper.createObjectNode();
        ext.set("ae", IntNode.valueOf(0));

        final Imp givenImp = Imp.builder().ext(ext).build();

        final List<String> debugMessages = new ArrayList<>();

        // when
        final Imp result = target.adjust(givenImp, "someBidder", bidderAliases, debugMessages);

        // then
        assertThat(result.getExt().get("igs").get(0).get("ae")).isEqualTo(IntNode.valueOf(0));
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void adjustShouldSetImpExtIgsAeWhenImpExtAeIsOne() {
        // given
        final ObjectNode ext = mapper.createObjectNode();
        ext.set("ae", IntNode.valueOf(1));

        final Imp givenImp = Imp.builder().ext(ext).build();

        final List<String> debugMessages = new ArrayList<>();

        // when
        final Imp result = target.adjust(givenImp, "someBidder", bidderAliases, debugMessages);

        // then
        assertThat(result.getExt().get("igs").get(0).get("ae")).isEqualTo(IntNode.valueOf(1));
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void adjustShouldNotSetImpExtIgsAeWhenImpExtAeIsNotZeroOrOne() {
        // given
        final ObjectNode ext = mapper.createObjectNode();
        ext.set("ae", IntNode.valueOf(3));

        final Imp givenImp = Imp.builder().ext(ext).build();

        final List<String> debugMessages = new ArrayList<>();

        // when
        final Imp result = target.adjust(givenImp, "someBidder", bidderAliases, debugMessages);

        // then
        assertThat(result.getExt().get("igs")).isNull();
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void adjustShouldNotModifyImpExtIgsAeWhenImpExtIgsAePresent() {
        // given
        final ObjectNode ext = mapper.createObjectNode();
        ext.set("ae", IntNode.valueOf(0));
        ext.set("igs", mapper.createArrayNode().add(mapper.createObjectNode().set("ae", IntNode.valueOf(123))));

        final Imp givenImp = Imp.builder().ext(ext).build();

        final List<String> debugMessages = new ArrayList<>();

        // when
        final Imp result = target.adjust(givenImp, "someBidder", bidderAliases, debugMessages);

        // then
        assertThat(result.getExt().get("igs").get(0).get("ae")).isEqualTo(IntNode.valueOf(123));
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void adjustShouldRemoveExpImpFromOriginalImpWhenImpExtPrebidImpHasEmptyBidder() {
        // given
        final Imp givenImp = Imp.builder()
                .ext(mapper.createObjectNode().set("prebid", mapper.createObjectNode()
                        .set("imp", mapper.createObjectNode().set("someBidder", mapper.createObjectNode()))))
                .build();
        final List<String> debugMessages = new ArrayList<>();

        // when
        final Imp result = target.adjust(givenImp, "someBidder", bidderAliases, debugMessages);

        // then
        final Imp expectedImp = givenImp.toBuilder()
                .ext(mapper.createObjectNode().set("prebid", mapper.createObjectNode())).build();

        assertThat(result).isEqualTo(expectedImp);
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void resolveImpShouldMergeBidderSpecificImpIntoOriginalImp() throws ValidationException {
        // given
        final ObjectNode givenBidderImp = mapper.createObjectNode()
                .put("bidfloor", "2.0")
                .set("pmp", mapper.createObjectNode()
                        .set("deals", mapper.createArrayNode()
                                .add(mapper.createObjectNode().put("id", "dealId2"))));

        final Imp givenImp = givenImp("someBidder", givenBidderImp);

        final List<String> debugMessages = new ArrayList<>();

        // when
        final Imp result = target.adjust(givenImp, "someBidder", bidderAliases, debugMessages);

        // then
        final Imp expectedImp = givenImp.toBuilder()
                .pmp(Pmp.builder().deals(Collections.singletonList(Deal.builder().id("dealId2").build())).build())
                .bidfloor(new BigDecimal("2.0"))
                .ext(mapper.createObjectNode().put("originAttr", "originValue")
                        .set("prebid", mapper.createObjectNode().put("prebidOriginAttr", "prebidOriginValue")))
                .build();

        verify(impValidator).validateImp(expectedImp);
        assertThat(result).isEqualTo(expectedImp);
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void resolveImpShouldMergeBidderSpecificImpIntoOriginalImpCaseInsensitive() throws ValidationException {
        // given
        final ObjectNode givenBidderImp = mapper.createObjectNode()
                .put("bidfloor", "2.0")
                .set("pmp", mapper.createObjectNode()
                        .set("deals", mapper.createArrayNode()
                                .add(mapper.createObjectNode().put("id", "dealId2"))));

        final Imp givenImp = givenImp("someBidder", givenBidderImp);
        final List<String> debugMessages = new ArrayList<>();

        // when
        final Imp result = target.adjust(givenImp, "SOMEbiDDer", bidderAliases, debugMessages);

        // then
        final Imp expectedImp = givenImp.toBuilder()
                .pmp(Pmp.builder().deals(Collections.singletonList(Deal.builder().id("dealId2").build())).build())
                .bidfloor(new BigDecimal("2.0"))
                .ext(mapper.createObjectNode().put("originAttr", "originValue")
                        .set("prebid", mapper.createObjectNode().put("prebidOriginAttr", "prebidOriginValue")))
                .build();

        verify(impValidator).validateImp(expectedImp);
        assertThat(result).isEqualTo(expectedImp);
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void resolveImpShouldMergeBidderSpecificImpIntoOriginalImpCaseAliasBidder() throws ValidationException {
        // given
        final ObjectNode givenBidderImp = mapper.createObjectNode()
                .put("bidfloor", "2.0")
                .set("pmp", mapper.createObjectNode()
                        .set("deals", mapper.createArrayNode()
                                .add(mapper.createObjectNode().put("id", "dealId2"))));

        final Imp givenImp = givenImp("someBidderAlias", givenBidderImp);
        final List<String> debugMessages = new ArrayList<>();

        // when
        final Imp result = target.adjust(givenImp, "SOMEbiDDer", bidderAliases, debugMessages);

        // then
        final Imp expectedImp = givenImp.toBuilder()
                .pmp(Pmp.builder().deals(Collections.singletonList(Deal.builder().id("dealId2").build())).build())
                .bidfloor(new BigDecimal("2.0"))
                .ext(mapper.createObjectNode().put("originAttr", "originValue")
                        .set("prebid", mapper.createObjectNode().put("prebidOriginAttr", "prebidOriginValue")))
                .build();

        verify(impValidator).validateImp(expectedImp);
        assertThat(result).isEqualTo(expectedImp);
        assertThat(debugMessages).isEmpty();
    }

    @Test
    public void resolveImpShouldReturnImpWithoutExpImpWhenResultingImpValidationFailed() throws ValidationException {
        // given
        doThrow(new ValidationException("imp validation failed")).when(impValidator).validateImp(any());

        final ObjectNode givenBidderImp = mapper.createObjectNode()
                .put("bidfloor", "2.0")
                .set("pmp", mapper.createObjectNode()
                        .set("deals", mapper.createArrayNode()
                                .add(mapper.createObjectNode().put("id", "dealId2"))));

        final Imp givenImp = givenImp("someBidder", givenBidderImp);
        final List<String> debugMessages = new ArrayList<>();

        // when
        final Imp result = target.adjust(givenImp, "someBidder", bidderAliases, debugMessages);

        // then
        final Imp expectedImp = givenImp.toBuilder()
                .ext(mapper.createObjectNode().put("originAttr", "originValue")
                        .set("prebid", mapper.createObjectNode().put("prebidOriginAttr", "prebidOriginValue")))
                .build();

        assertThat(result).isEqualTo(expectedImp);
        assertThat(debugMessages).containsOnly(
                "imp.ext.prebid.imp.someBidder can not be merged into original imp [id=impId], "
                        + "reason: imp validation failed");
    }

    @Test
    public void resolveImpShouldReturnImpWithoutExpWhenMergingFailed() {
        // given
        final ObjectNode invalidBidderImp = mapper.createObjectNode()
                .put("bidfloor", "2.0")
                .put("pmp", 3);

        final Imp givenImp = givenImp("someBidder", invalidBidderImp);
        final List<String> debugMessages = new ArrayList<>();

        // when
        final Imp result = target.adjust(givenImp, "someBidder", bidderAliases, debugMessages);

        // then
        final Imp expectedImp = givenImp.toBuilder()
                .ext(mapper.createObjectNode().put("originAttr", "originValue")
                        .set("prebid", mapper.createObjectNode().put("prebidOriginAttr", "prebidOriginValue")))
                .build();

        assertThat(result).isEqualTo(expectedImp);
        assertThat(debugMessages).hasSize(1).first()
                .satisfies(message -> assertThat(message).startsWith(
                        "imp.ext.prebid.imp.someBidder can not be merged into original imp [id=impId],"
                                + " reason: Cannot construct instance of `com.iab.openrtb.request.Pmp`"));
    }

    private static Imp givenImp(String bidder, ObjectNode bidderImpNode) {
        final JsonNode givenExtPrebid = mapper.createObjectNode()
                .put("prebidOriginAttr", "prebidOriginValue")
                .set("imp", mapper.createObjectNode().set(bidder, bidderImpNode));

        return Imp.builder()
                .id("impId")
                .tagid("impTagId")
                .bidfloor(new BigDecimal("1.0"))
                .bidfloorcur("USD")
                .secure(1)
                .pmp(Pmp.builder().deals(Collections.singletonList(Deal.builder().id("dealId").build())).build())
                .iframebuster(Collections.singletonList("iframebuster"))
                .ext(mapper.createObjectNode().put("originAttr", "originValue").set("prebid", givenExtPrebid))
                .build();
    }

}
