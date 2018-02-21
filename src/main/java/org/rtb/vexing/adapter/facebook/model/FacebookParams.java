package org.rtb.vexing.adapter.facebook.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class FacebookParams {

    String placementId;
}
