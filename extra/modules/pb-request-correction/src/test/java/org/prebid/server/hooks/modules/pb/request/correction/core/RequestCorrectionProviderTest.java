package org.prebid.server.hooks.modules.pb.request.correction.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.pb.request.correction.core.correction.Correction;
import org.prebid.server.hooks.modules.pb.request.correction.core.correction.CorrectionProducer;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class RequestCorrectionProviderTest {

    @Mock
    private CorrectionProducer correctionProducer;

    private RequestCorrectionProvider target;

    @BeforeEach
    public void setUp() {
        target = new RequestCorrectionProvider(singletonList(correctionProducer));
    }

    @Test
    public void correctionsShouldReturnEmptyListIfAllCorrectionsDisabled() {
        // given
        given(correctionProducer.shouldProduce(any(), any())).willReturn(false);

        // when
        final List<Correction> corrections = target.corrections(null, null);

        // then
        assertThat(corrections).isEmpty();
    }

    @Test
    public void correctionsShouldReturnProducedCorrection() {
        // given
        given(correctionProducer.shouldProduce(any(), any())).willReturn(true);

        final Correction correction = mock(Correction.class);
        given(correctionProducer.produce(any())).willReturn(correction);

        // when
        final List<Correction> corrections = target.corrections(null, null);

        // then
        assertThat(corrections).containsExactly(correction);
    }
}
