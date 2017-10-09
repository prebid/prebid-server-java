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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AdapterCatalogTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;

    private PublicSuffixList psl;

    @Before
    public void setupClass() {
        psl = new PublicSuffixListFactory().build();
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatThrownBy(() -> new AdapterCatalog(null, null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdapterCatalog(new JsonObject(), null, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AdapterCatalog(new JsonObject(), httpClient, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void creationShouldFailOnIncompleteConfig() {
        assertThatThrownBy(() -> new AdapterCatalog(new JsonObject(), httpClient, psl))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void getShouldReturnConfiguredAdapter() {
        // given
        final JsonObject config = new JsonObject();
        final JsonObject adaptersConfig = new JsonObject();
        config.put("adapters", adaptersConfig);
        final JsonObject rubiconConfig = new JsonObject();
        rubiconConfig.put("endpoint", "http://rubiconproject.com/x");
        rubiconConfig.put("usersync_url", "http://rubiconproject.com/x/cookie");
        adaptersConfig.put("rubicon", rubiconConfig);
        final JsonObject xapiConfig = new JsonObject();
        xapiConfig.put("Username", "rubicon_user");
        xapiConfig.put("Password", "rubicon_password");
        rubiconConfig.put("XAPI", xapiConfig);

        assertThat(new AdapterCatalog(config, httpClient, psl).get("rubicon"))
                .isNotNull()
                .isInstanceOf(RubiconAdapter.class);
    }
}
