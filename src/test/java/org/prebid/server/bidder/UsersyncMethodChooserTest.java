package org.prebid.server.bidder;

import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.request.CookieSyncRequest;

import static org.assertj.core.api.Assertions.assertThat;

public class UsersyncMethodChooserTest extends VertxTest {

    private static final String BIDDER = "bidder";

    @Test
    public void shouldPreferIframeOverRedirect() {

    }

    @Test
    public void shouldReturnPreferredMethodWhenFilterIsNull() {
        // given and when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(null)
                .choose(iframeUsersyncer("url"), BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(iframeMethod("url"));
    }

    @Test
    public void shouldReturnPreferredMethodWhenFilterIsEmpty() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(null, null);

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(iframeUsersyncer("url"), BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(iframeMethod("url"));
    }

    @Test
    public void shouldReturnPrimaryMethodWhenMethodFilterTypeIsNull() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(null, null),
                null);

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(iframeUsersyncer("url"), BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(iframeMethod("url"));
    }

    @Test
    public void shouldReturnRedirectMethodWhenIframeMethodFilterExcludeAndNullBidders() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        null,
                        CookieSyncRequest.FilterType.exclude),
                null);
        final Usersyncer usersyncer = Usersyncer.of(null, iframeMethod("url"), redirectMethod("url"));

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(redirectMethod("url"));
    }

    @Test
    public void shouldReturnPrimaryMethodWhenNotInMethodFilterExcludeList() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        mapper.createArrayNode().add("anotherbidder"),
                        CookieSyncRequest.FilterType.exclude),
                null);

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(iframeUsersyncer("url"), BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(iframeMethod("url"));
    }

    @Test
    public void shouldReturnSecondaryMethodWhenInMethodFilterExcludeList() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        mapper.createArrayNode().add(BIDDER),
                        CookieSyncRequest.FilterType.exclude),
                null);
        final Usersyncer usersyncer = Usersyncer.of(null, iframeMethod("url"), redirectMethod("url"));

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(redirectMethod("url"));
    }

    @Test
    public void shouldReturnSecondaryMethodWhenMethodFilterExcludesAll() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        new TextNode("*"),
                        CookieSyncRequest.FilterType.exclude),
                null);
        final Usersyncer usersyncer = Usersyncer.of(null, iframeMethod("url"), redirectMethod("url"));

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(redirectMethod("url"));
    }

    @Test
    public void shouldReturnPrimaryMethodWhenMethodFilterExcludeListIsNotArray() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        new IntNode(1),
                        CookieSyncRequest.FilterType.exclude),
                null);

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(iframeUsersyncer("url"), BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(iframeMethod("url"));
    }

    @Test
    public void shouldReturnPrimaryMethodWhenMethodFilterExcludeListIsNotStringArray() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        mapper.createArrayNode().add(1),
                        CookieSyncRequest.FilterType.exclude),
                null);

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(iframeUsersyncer("url"), BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(iframeMethod("url"));
    }

    @Test
    public void shouldReturnPrimaryMethodWhenMethodFilterIncludeAndNullBidders() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        null,
                        CookieSyncRequest.FilterType.include),
                null);

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(iframeUsersyncer("url"), BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(iframeMethod("url"));
    }

    @Test
    public void shouldReturnSecondaryMethodWhenNotInMethodFilterIncludeList() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        mapper.createArrayNode().add("anotherbidder"),
                        CookieSyncRequest.FilterType.include),
                null);
        final Usersyncer usersyncer = Usersyncer.of(null, iframeMethod("url"), redirectMethod("url"));

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(redirectMethod("url"));
    }

    @Test
    public void shouldReturnPrimaryMethodWhenInMethodFilterIncludeList() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        mapper.createArrayNode().add(BIDDER),
                        CookieSyncRequest.FilterType.include),
                null);

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(iframeUsersyncer("url"), BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(iframeMethod("url"));
    }

    @Test
    public void shouldReturnPrimaryMethodWhenMethodFilterIncludesAll() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        new TextNode("*"),
                        CookieSyncRequest.FilterType.include),
                null);

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(iframeUsersyncer("url"), BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(iframeMethod("url"));
    }

    @Test
    public void shouldReturnSecondaryMethodWhenMethodFilterIncludeListIsNotArray() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        new IntNode(1),
                        CookieSyncRequest.FilterType.include),
                null);
        final Usersyncer usersyncer = Usersyncer.of(null, iframeMethod("url"), redirectMethod("url"));

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(redirectMethod("url"));
    }

    @Test
    public void shouldReturnSecondaryMethodWhenMethodFilterIncludeListIsNotStringArray() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        mapper.createArrayNode().add(1),
                        CookieSyncRequest.FilterType.include),
                null);
        final Usersyncer usersyncer = Usersyncer.of(null, iframeMethod("url"), redirectMethod("url"));

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(redirectMethod("url"));
    }

    @Test
    public void shouldReturnSecondaryMethodWhenPrimaryIsFilteredOutAndSecondIsNot() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        new TextNode("*"),
                        CookieSyncRequest.FilterType.exclude),
                CookieSyncRequest.MethodFilter.of(
                        new TextNode("*"),
                        CookieSyncRequest.FilterType.include));
        final Usersyncer usersyncer = Usersyncer.of(null, iframeMethod("url"), redirectMethod("url"));

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isEqualTo(redirectMethod("url"));
    }

    @Test
    public void shouldReturnNullWhenPrimaryAndSecondaryAreFilteredOut() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        new TextNode("*"),
                        CookieSyncRequest.FilterType.exclude),
                CookieSyncRequest.MethodFilter.of(
                        new TextNode("*"),
                        CookieSyncRequest.FilterType.exclude));
        final Usersyncer usersyncer = Usersyncer.of(null, iframeMethod("url"), redirectMethod("url"));

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isNull();
    }

    @Test
    public void shouldReturnNullWhenPrimaryHasNoUrl() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(null, null);

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(iframeUsersyncer(null), BIDDER);

        // then
        assertThat(chosenMethod).isNull();
    }

    @Test
    public void shouldReturnNullWhenPrimaryIsFilteredOutAndNoSecondary() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        new TextNode("*"),
                        CookieSyncRequest.FilterType.exclude),
                null);

        // when
        final UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter)
                .choose(iframeUsersyncer("url"), BIDDER);

        // then
        assertThat(chosenMethod).isNull();
    }

    private Usersyncer iframeUsersyncer(String url) {
        return Usersyncer.of(null, iframeMethod(url), null);
    }

    private UsersyncMethod iframeMethod(String url) {
        return UsersyncMethod.builder()
                .type(UsersyncMethodType.IFRAME)
                .usersyncUrl(url)
                .redirectUrl(null)
                .supportCORS(false)
                .build();
    }

    private UsersyncMethod redirectMethod(String url) {
        return UsersyncMethod.builder()
                .type(UsersyncMethodType.REDIRECT)
                .usersyncUrl(url)
                .redirectUrl(null)
                .supportCORS(false)
                .build();
    }
}
