package org.rtb.vexing.adapter;

import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.JsonObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.adapter.rubicon.RubiconAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

public class AdapterCatalogTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;

    private PublicSuffixList psl;

    @Before
    public void setupClass() {
        psl = new PublicSuffixListFactory().build();
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new AdapterCatalog(null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new AdapterCatalog(new JsonObject(), null, null));
        assertThatNullPointerException().isThrownBy(() -> new AdapterCatalog(new JsonObject(), httpClient, null));
    }

    @Test
    public void creationShouldFailOnIncompleteConfig() {
        assertThatNullPointerException().isThrownBy(() -> new AdapterCatalog(new JsonObject(), httpClient, psl));
    }

    @Test
    public void getShouldReturnConfiguredAdapter() {
        // given
        final JsonObject config = new JsonObject()
                .put("adapters", new JsonObject()
                        .put("rubicon", new JsonObject()
                                .put("endpoint", "http://rubiconproject.com/x")
                                .put("usersync_url", "http://rubiconproject.com/x/cookie")
                                .put("XAPI", new JsonObject()
                                        .put("Username", "rubicon_user")
                                        .put("Password", "rubicon_password"))));

        // when
        final Adapter rubiconAdapter = new AdapterCatalog(config, httpClient, psl).get("rubicon");

        // then
        assertThat(rubiconAdapter)
                .isNotNull()
                .isInstanceOf(RubiconAdapter.class);
    }
}
