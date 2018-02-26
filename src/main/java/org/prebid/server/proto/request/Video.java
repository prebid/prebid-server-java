package org.prebid.server.proto.request;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public final class Video {

    /*
     * Content MIME types supported. Popular MIME types may include “video/x-ms-wmv”
     * for Windows Media and “video/x-flv” for Flash Video.
     */
    List<String> mimes;

    /*
     * Minimum video ad duration in seconds.
     */
    Integer minduration;

    /*
     * Maximum video ad duration in seconds.
     */
    Integer maxduration;

    /*
     * Indicates the start delay in seconds for pre-roll, mid-roll, or post-roll ad placements.
     */
    Integer startdelay;

    /*
     * Indicates if the player will allow the video to be skipped ( 0 = no, 1 = yes).
     */
    Integer skippable;

    /*
     * Playback method code Description
     * 1 - Initiates on Page Load with Sound On
     * 2 - Initiates on Page Load with Sound Off by Default
     * 3 - Initiates on Click with Sound On
     * 4 - Initiates on Mouse-Over with Sound On
     * 5 - Initiates on Entering Viewport with Sound On
     * 6 - Initiates on Entering Viewport with Sound Off by Default
     */
    Integer playbackMethod;

    /*
     * protocols as specified in ORTB 5.8
     * 1 VAST 1.0
     * 2 VAST 2.0
     * 3 VAST 3.0
     *
     * 4 VAST 1.0 Wrapper
     * 5 VAST 2.0 Wrapper
     * 6 VAST 3.0 Wrapper
     * 7 VAST 4.0
     * 8 VAST 4.0 Wrapper
     * 9 DAAST 1.0
     * 10 DAAST 1.0 Wrapper
     */
    List<Integer> protocols;
}
