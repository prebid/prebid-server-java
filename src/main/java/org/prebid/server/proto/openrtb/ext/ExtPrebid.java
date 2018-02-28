package org.prebid.server.proto.openrtb.ext;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.bidder.Bidder;

/**
 * Defines the contract for any extension that has "prebid" and "bidder" fields.
 * <p>
 * Can be used by {@link Bidder}s to unmarshal any request.imp[i].ext.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtPrebid<P, B> {

    P prebid;

    /**
     * Contains the bidder-specific extension.
     * <p>
     * Each bidder should specify their corresponding ExtImp{Bidder} class as a type argument when unmarshaling
     * extension using this class.
     * <p>
     * Bidder implementations may safely assume that this extension has been validated by their parameters schema.
     */
    B bidder;
}
