package org.prebid.server.hooks.v1;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class HookCatalogTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Module sampleModule;
    @Mock
    private Hook sampleHook;

    private HookCatalog hookCatalog;

    @Before
    public void setUp() {
        given(sampleModule.code()).willReturn("sample-module");
        given(sampleModule.hooks()).willReturn(singleton(sampleHook));
        given(sampleHook.name()).willReturn("sample-hook");

        hookCatalog = new HookCatalog(singleton(sampleModule));
    }

    @Test
    public void getHookByShouldReturnHookByModuleCodeAndHookImplCode() {
        // when
        final Hook foundHook = hookCatalog.getHookBy("sample-module", "sample-hook");

        // then
        assertThat(foundHook).isNotNull()
                .extracting(Hook::name)
                .containsOnly("sample-hook");
    }

    @Test
    public void getHookByShouldTolerateUnknownModule() {
        // when
        final Hook foundHook = hookCatalog.getHookBy("unknown-module", null);

        // then
        assertThat(foundHook).isNull();
    }

    @Test
    public void getHookByShouldTolerateUnknownHook() {
        // when
        final Hook foundHook = hookCatalog.getHookBy("sample-module", "unknown-hook");

        // then
        assertThat(foundHook).isNull();
    }
}
