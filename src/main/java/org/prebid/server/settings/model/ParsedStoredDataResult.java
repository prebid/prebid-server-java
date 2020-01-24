package org.prebid.server.settings.model;

import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.video.BidRequestVideo;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;
import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class ParsedStoredDataResult {

    BidRequestVideo storedData;

    Map<String, Imp> idToImps;

    List<String> errors;
}

