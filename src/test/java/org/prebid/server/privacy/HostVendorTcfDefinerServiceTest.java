package org.prebid.server.privacy;

import io.vertx.core.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.HostVendorTcfResponse;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.model.TcfResponse;

import java.util.Map;

import static java.util.Collections.singleton;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.prebid.server.assertion.FutureAssertion.assertThat;

@ExtendWith(MockitoExtension.class)
public class HostVendorTcfDefinerServiceTest {

    @Mock
    private TcfDefinerService tcfDefinerService;

    private HostVendorTcfDefinerService target;

    @Test
    public void isAllowedForHostVendorIdShouldReturnAllowedVendorWhenHostVendorIdIsNull() {
        // given
        target = new HostVendorTcfDefinerService(tcfDefinerService, null);

        final TcfContext tcfContext = TcfContext.empty();

        // when
        final Future<HostVendorTcfResponse> result = target.isAllowedForHostVendorId(tcfContext);

        // then
        verifyNoInteractions(tcfDefinerService);
        assertThat(result).succeededWith(HostVendorTcfResponse.allowedVendor());
    }

    @Test
    public void isAllowedForHostVendorIdShouldReturnDelegateDecisionToTcfDefinerServiceHostVendorIdIsNotNull() {
        // given
        target = new HostVendorTcfDefinerService(tcfDefinerService, 1);
        final TcfContext tcfContext = TcfContext.empty();
        final TcfResponse<Integer> tcfResponse = TcfResponse.of(
                true,
                Map.of(1, PrivacyEnforcementAction.restrictAll()),
                "country");

        given(tcfDefinerService.resultForVendorIds(singleton(1), tcfContext))
                .willReturn(Future.succeededFuture(tcfResponse));

        // when
        final Future<HostVendorTcfResponse> result = target.isAllowedForHostVendorId(tcfContext);

        // then
        verify(tcfDefinerService).resultForVendorIds(singleton(1), tcfContext);
        assertThat(result).succeededWith(HostVendorTcfResponse.of(true, "country", false));
    }
}
