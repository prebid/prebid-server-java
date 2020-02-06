package com.iab.openrtb.request.video;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class VideoVideo {
    /**
     * Content MIME types supported (e.g., “video/x-ms-wmv”, “video/mp4”).
     * (required)
     */
    List<String> mimes;

    /**
     * Width of the video player in device independent pixels (DIPS).
     * (recommended)
     */
    Integer w;

    /**
     * Height of the video player in device independent pixels (DIPS).
     * (recommended)
     */
    Integer h;

    /**
     * Array of supported video protocols. Refer to List 5.8. At least one
     * supported protocol must be specified in either the protocol or protocols
     * attribute. (recommended)
     */
    List<Integer> protocols;
}

