package org.rtb.vexing.cookie;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Cookie;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.model.Uids;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

public class UidsCookieTest {

    private static final String RUBICON = "rubicon";
    private static final String ADNXS = "adnxs";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new UidsCookie(null));
    }

    @Test
    public void uidFromShouldReturnUids() {
        // given
        final Map<String, String> uids = new HashMap<>();
        uids.put(RUBICON, "J5VLCWQP-26-CWFT");
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(uids).build());

        // when and then
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
    }

    @Test
    public void uidFromShouldTolerateNullUids() {
        // given
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().build());

        // when and then
        assertThat(uidsCookie.uidFrom(RUBICON)).isNull();
    }

    @Test
    public void allowsSyncShouldReturnFalseIfOptoutTrue() {
        // given
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().optout(true).build());

        // when and then
        assertThat(uidsCookie.allowsSync()).isFalse();
    }

    @Test
    public void allowsSyncShouldReturnTrueIfOptoutAbsent() {
        // given
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().build());

        // when and then
        assertThat(uidsCookie.allowsSync()).isTrue();
    }

    @Test
    public void hasLiveUidsShouldReturnFalse() {
        assertThat(new UidsCookie(Uids.builder().build()).hasLiveUids()).isFalse();
    }

    @Test
    public void isFacebookSentinelShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> UidsCookie.isFacebookSentinel(null, null));
        assertThatNullPointerException().isThrownBy(() -> UidsCookie.isFacebookSentinel("", null));
    }

    @Test
    public void isFacebookSentinelShouldReturnTrueForAudienceNetworkAndZero() {
        assertThat(UidsCookie.isFacebookSentinel("audienceNetwork", "0")).isTrue();
    }

    @Test
    public void isFacebookSentinelShouldReturnFalseForAudienceNetworkAndNonZero() {
        assertThat(UidsCookie.isFacebookSentinel("audienceNetwork", "id")).isFalse();
    }

    @Test
    public void isFacebookSentinelShouldReturnFalseForNonAudienceNetwork() {
        assertThat(UidsCookie.isFacebookSentinel("rubicon", "0")).isFalse();
    }

    @Test
    public void deleteUidShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new UidsCookie(Uids.builder().build()).deleteUid(null));
    }

    @Test
    public void deleteUidShouldReturnUidsCookieWithUidRemoved() {
        // given
        final Map<String, String> uids = new HashMap<>();
        uids.put(RUBICON, "J5VLCWQP-26-CWFT");
        uids.put(ADNXS, "12345");
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(uids).build());

        // when
        final UidsCookie uidsCookieReturned = uidsCookie.deleteUid(RUBICON);

        // then
        assertThat(uidsCookieReturned.uidFrom(RUBICON)).isNull();
        assertThat(uidsCookieReturned.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void deleteUidShouldIgnoreMissingUid() {
        // given
        final Map<String, String> uids = new HashMap<>();
        uids.put(ADNXS, "12345");
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(uids).build());

        // when
        final UidsCookie uidsCookieReturned = uidsCookie.deleteUid(RUBICON);

        // then
        assertThat(uidsCookieReturned.uidFrom(RUBICON)).isNull();
        assertThat(uidsCookieReturned.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void deleteUidShouldTolerateNullUids() {
        // given
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().build());

        // when
        final UidsCookie uidsCookieReturned = uidsCookie.deleteUid(RUBICON);

        // then
        assertThat(uidsCookieReturned.uidFrom(RUBICON)).isNull();
    }

    @Test
    public void updateUidShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new UidsCookie(Uids.builder().build()).updateUid(null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new UidsCookie(Uids.builder().build()).updateUid("", null));
    }

    @Test
    public void updateUidShouldReturnUidsCookieWithUidReplaced() {
        // given
        final Map<String, String> uids = new HashMap<>();
        uids.put(RUBICON, "J5VLCWQP-26-CWFT");
        uids.put(ADNXS, "12345");
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(uids).build());

        // when
        final UidsCookie uidsCookieReturned = uidsCookie.updateUid(RUBICON, "updatedUid");

        // then
        assertThat(uidsCookieReturned.uidFrom(RUBICON)).isEqualTo("updatedUid");
        assertThat(uidsCookieReturned.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void updateUidShouldReturnUidsCookieWithUidAdded() {
        // given
        final Map<String, String> uids = new HashMap<>();
        uids.put(ADNXS, "12345");
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(uids).build());

        // when
        final UidsCookie uidsCookieReturned = uidsCookie.updateUid(RUBICON, "createdUid");

        // then
        assertThat(uidsCookieReturned.uidFrom(RUBICON)).isEqualTo("createdUid");
        assertThat(uidsCookieReturned.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void updateUidShouldTolerateNullUids() {
        // given
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().build());

        // when
        final UidsCookie uidsCookieReturned = uidsCookie.updateUid(RUBICON, "createdUid");

        // then
        assertThat(uidsCookieReturned.uidFrom(RUBICON)).isEqualTo("createdUid");
    }

    @Test
    public void toCookieShouldReturnCookieWithExpectedValue() {
        // given
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(new HashMap<>()).build())
                .updateUid(RUBICON, "rubiconUid")
                .updateUid(ADNXS, "adnxsUid");

        // when
        final Cookie cookie = uidsCookie.toCookie();

        // then
        final Uids uids = Json.decodeValue(Buffer.buffer(Base64.getUrlDecoder().decode(cookie.getValue())), Uids.class);
        assertThat(uids.uids).containsOnly(entry(RUBICON, "rubiconUid"), entry(ADNXS, "adnxsUid"));
    }

    @Test
    public void toCookieShouldReturnCookieWithExpectedExpiration() {
        // when
        final Cookie uidsCookie = new UidsCookie(Uids.builder().uids(new HashMap<>()).build()).toCookie();

        // then
        assertThat(uidsCookie.encode()).containsSequence("Max-Age=15552000; Expires=");
    }
}
