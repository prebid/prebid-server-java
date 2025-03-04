package org.prebid.server.bidder.resetdigital.request;

import lombok.Builder;
import lombok.Value;

@Value(staticConstructor = "of")
@Builder
public class ResetDigitalImpMediaTypes {

    ResetDigitalImpMediaType banner;

    ResetDigitalImpMediaType video;

    ResetDigitalImpMediaType audio;

    public static ResetDigitalImpMediaTypes banner(ResetDigitalImpMediaType banner) {
        return ResetDigitalImpMediaTypes.builder().banner(banner).build();
    }

    public static ResetDigitalImpMediaTypes video(ResetDigitalImpMediaType video) {
        return ResetDigitalImpMediaTypes.builder().video(video).build();
    }

    public static ResetDigitalImpMediaTypes audio(ResetDigitalImpMediaType audio) {
        return ResetDigitalImpMediaTypes.builder().audio(audio).build();
    }
}
