package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;
import org.prebid.server.bidder.Bidder;

/**
 * Defines the contract for any extension that has "prebid" and "bidder" fields.
 * <p>
 * Can be used by {@link Bidder}s to unmarshal any request.imp[i].ext.
 */

@Value(staticConstructor = "of")
public class ExtImp<P, B> {

    public static <P, B> ExtImp<P, B> of(B bidder) {
        return of(null, bidder);
    }

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
