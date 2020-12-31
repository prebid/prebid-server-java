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
    public void shouldReturnPrimaryMethodWhenFilterIsNull() {
        // given
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(null).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(primaryMethod);
    }

    @Test
    public void shouldReturnPrimaryMethodWhenFilterIsEmpty() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(null, null);
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(primaryMethod);
    }

    @Test
    public void shouldReturnPrimaryMethodWhenMethodFilterTypeIsNull() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(null, null),
                null);
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(primaryMethod);
    }

    @Test
    public void shouldReturnSecondaryMethodWhenMethodFilterExcludeAndNullBidders() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        null,
                        CookieSyncRequest.FilterType.exclude),
                null);
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer.UsersyncMethod secondaryMethod = createMethod("redirect", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod, secondaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(secondaryMethod);
    }

    @Test
    public void shouldReturnPrimaryMethodWhenNotInMethodFilterExcludeList() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        mapper.createArrayNode().add("anotherbidder"),
                        CookieSyncRequest.FilterType.exclude),
                null);
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(primaryMethod);
    }

    @Test
    public void shouldReturnSecondaryMethodWhenInMethodFilterExcludeList() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        mapper.createArrayNode().add(BIDDER),
                        CookieSyncRequest.FilterType.exclude),
                null);
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer.UsersyncMethod secondaryMethod = createMethod("redirect", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod, secondaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(secondaryMethod);
    }

    @Test
    public void shouldReturnSecondaryMethodWhenMethodFilterExcludesAll() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        new TextNode("*"),
                        CookieSyncRequest.FilterType.exclude),
                null);
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer.UsersyncMethod secondaryMethod = createMethod("redirect", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod, secondaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(secondaryMethod);
    }

    @Test
    public void shouldReturnPrimaryMethodWhenMethodFilterExcludeListIsNotArray() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        new IntNode(1),
                        CookieSyncRequest.FilterType.exclude),
                null);
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(primaryMethod);
    }

    @Test
    public void shouldReturnPrimaryMethodWhenMethodFilterExcludeListIsNotStringArray() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        mapper.createArrayNode().add(1),
                        CookieSyncRequest.FilterType.exclude),
                null);
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(primaryMethod);
    }

    @Test
    public void shouldReturnPrimaryMethodWhenMethodFilterIncludeAndNullBidders() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        null,
                        CookieSyncRequest.FilterType.include),
                null);
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(primaryMethod);
    }

    @Test
    public void shouldReturnSecondaryMethodWhenNotInMethodFilterIncludeList() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        mapper.createArrayNode().add("anotherbidder"),
                        CookieSyncRequest.FilterType.include),
                null);
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer.UsersyncMethod secondaryMethod = createMethod("redirect", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod, secondaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(secondaryMethod);
    }

    @Test
    public void shouldReturnPrimaryMethodWhenInMethodFilterIncludeList() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        mapper.createArrayNode().add(BIDDER),
                        CookieSyncRequest.FilterType.include),
                null);
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(primaryMethod);
    }

    @Test
    public void shouldReturnPrimaryMethodWhenMethodFilterIncludesAll() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        new TextNode("*"),
                        CookieSyncRequest.FilterType.include),
                null);
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(primaryMethod);
    }

    @Test
    public void shouldReturnSecondaryMethodWhenMethodFilterIncludeListIsNotArray() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        new IntNode(1),
                        CookieSyncRequest.FilterType.include),
                null);
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer.UsersyncMethod secondaryMethod = createMethod("redirect", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod, secondaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(secondaryMethod);
    }

    @Test
    public void shouldReturnSecondaryMethodWhenMethodFilterIncludeListIsNotStringArray() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(
                CookieSyncRequest.MethodFilter.of(
                        mapper.createArrayNode().add(1),
                        CookieSyncRequest.FilterType.include),
                null);
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer.UsersyncMethod secondaryMethod = createMethod("redirect", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod, secondaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(secondaryMethod);
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
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer.UsersyncMethod secondaryMethod = createMethod("redirect", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod, secondaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isSameAs(secondaryMethod);
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
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer.UsersyncMethod secondaryMethod = createMethod("redirect", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod, secondaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isNull();
    }

    @Test
    public void shouldReturnNullWhenPrimaryHasNoUrl() {
        // given
        final CookieSyncRequest.FilterSettings filter = CookieSyncRequest.FilterSettings.of(null, null);
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", null);
        final Usersyncer usersyncer = createUsersyncer(primaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

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
        final Usersyncer.UsersyncMethod primaryMethod = createMethod("iframe", "url");
        final Usersyncer usersyncer = createUsersyncer(primaryMethod);

        // when
        final Usersyncer.UsersyncMethod chosenMethod = UsersyncMethodChooser.from(filter).choose(usersyncer, BIDDER);

        // then
        assertThat(chosenMethod).isNull();
    }

    private Usersyncer createUsersyncer(Usersyncer.UsersyncMethod primaryMethod) {
        return createUsersyncer(primaryMethod, null);
    }

    private Usersyncer createUsersyncer(Usersyncer.UsersyncMethod primaryMethod,
                                        Usersyncer.UsersyncMethod secondaryMethod) {

        return Usersyncer.of(null, primaryMethod, secondaryMethod);
    }

    private Usersyncer.UsersyncMethod createMethod(String type, String url) {
        return Usersyncer.UsersyncMethod.of(type, url, null, false);
    }
}
