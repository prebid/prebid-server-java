package org.prebid.server.settings.model;

import com.iab.openrtb.request.Video;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class VideoStoredDataResult {

    private static final VideoStoredDataResult EMPTY = VideoStoredDataResult.of(Collections.emptyMap(),
            Collections.emptyList());

    Map<String, Video> impIdToStoredVideo;

    List<String> errors;

    public static VideoStoredDataResult empty() {
        return EMPTY;
    }
}
