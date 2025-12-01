package org.prebid.server.auction.model;

import com.iab.openrtb.request.video.PodError;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class WithPodErrors<T> {

    T data;

    List<PodError> podErrors;

}
