package org.prebid.server.auction.model;

import com.iab.openrtb.request.video.PodError;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class WithPodErrors<T> {

    T data;

    List<PodError> podErrors;

}
