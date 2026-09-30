package org.prebid.server.hooks.modules.intentiq.identity.v1.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Uid;
import com.iab.openrtb.request.User;
import org.junit.jupiter.api.Test;
import org.prebid.server.hooks.modules.intentiq.identity.cache.CacheKey;
import org.prebid.server.hooks.modules.intentiq.identity.cache.KeyType;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class FirstPartyKeyExtractorTest {

    private final FirstPartyKeyExtractor target = new FirstPartyKeyExtractor(10);

    @Test
    public void shouldReturnEmptyWhenNoIdentifiersPresent() {
        assertThat(target.candidateKeys(BidRequest.builder().build())).isEmpty();
    }

    @Test
    public void shouldOrderKeysByPriorityAndTagTypes() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder().eids(List.of(
                        eid("uidapi.com", "uid2-1"),
                        eid("pubcid.org", "pub-1"),
                        eid("intentiq.com", "iiq-1")))
                        .build())
                .device(Device.builder().ifa("ifa-1").ua("UA").ip("1.2.3.4").build())
                .build();

        // when
        final List<CacheKey> keys = target.candidateKeys(bidRequest);

        // then — iiq, pubcid, maid, other eid, device composite (unrecognized UA contributes nothing)
        assertThat(keys).containsExactly(
                new CacheKey("iiq:iiq-1", KeyType.THIRD_PARTY),
                new CacheKey("pubcid:pub-1", KeyType.FIRST_PARTY),
                new CacheKey("maid:ifa-1", KeyType.FIRST_PARTY),
                new CacheKey("uidapi.com:uid2-1", KeyType.FIRST_PARTY),
                new CacheKey("dev:ifa-1_1.2.3.4", KeyType.DEVICE));
    }

    @Test
    public void shouldNormalizeUserAgentInDeviceCompositeRatherThanUseRawString() {
        // given — a real iPhone Safari UA
        final String rawUa = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) "
                + "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ua(rawUa).ip("1.2.3.4").build())
                .build();

        // when
        final CacheKey deviceKey = target.candidateKeys(bidRequest).stream()
                .filter(key -> key.type() == KeyType.DEVICE)
                .findFirst()
                .orElseThrow();

        // then — normalized OS/browser/device tokens, never the raw UA string
        assertThat(deviceKey.key())
                .startsWith("dev:")
                .endsWith("_1.2.3.4")
                .contains("iOS")
                .doesNotContain(rawUa)
                .doesNotContain(" ");
    }

    @Test
    public void shouldTreatSharedidOrgAsPubcid() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder().eids(singletonList(eid("sharedid.org", "s-1"))).build())
                .build();

        // when / then
        assertThat(target.candidateKeys(bidRequest))
                .containsExactly(new CacheKey("pubcid:s-1", KeyType.FIRST_PARTY));
    }

    @Test
    public void shouldSkipMaidKeyWhenLimitAdTracking() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ifa("ifa-1").lmt(1).build())
                .build();

        // when / then — only the device composite remains, no maid: key
        assertThat(target.candidateKeys(bidRequest))
                .extracting(CacheKey::key)
                .doesNotContain("maid:ifa-1");
    }

    @Test
    public void shouldUppercaseMaidForCtv() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .device(Device.builder().ifa("rida-abc").devicetype(3).build())
                .build();

        // when / then
        assertThat(target.candidateKeys(bidRequest))
                .extracting(CacheKey::key)
                .contains("maid:RIDA-ABC");
    }

    @Test
    public void shouldDeduplicateRepeatedKeys() {
        // given — same pubcid id twice
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder().eids(List.of(eid("pubcid.org", "dup"), eid("pubcid.org", "dup"))).build())
                .build();

        // when / then
        assertThat(target.candidateKeys(bidRequest))
                .containsExactly(new CacheKey("pubcid:dup", KeyType.FIRST_PARTY));
    }

    @Test
    public void shouldCapNumberOfKeys() {
        // given — extractor capped at 2
        final FirstPartyKeyExtractor capped = new FirstPartyKeyExtractor(2);
        final BidRequest bidRequest = BidRequest.builder()
                .user(User.builder().eids(List.of(
                        eid("intentiq.com", "iiq-1"),
                        eid("pubcid.org", "pub-1"),
                        eid("uidapi.com", "uid2-1")))
                        .build())
                .build();

        // when / then — only the two highest-priority keys survive
        assertThat(capped.candidateKeys(bidRequest))
                .containsExactly(
                        new CacheKey("iiq:iiq-1", KeyType.THIRD_PARTY),
                        new CacheKey("pubcid:pub-1", KeyType.FIRST_PARTY));
    }

    private static Eid eid(String source, String id) {
        return Eid.builder().source(source).uids(singletonList(Uid.builder().id(id).build())).build();
    }
}
