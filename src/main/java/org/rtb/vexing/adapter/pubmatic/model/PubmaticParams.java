package org.rtb.vexing.adapter.pubmatic.model;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public final class PubmaticParams {

    String publisherId;

    String adSlot;
}
