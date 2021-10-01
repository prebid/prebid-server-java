package org.prebid.server.proto.openrtb.ext;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.prebid.server.bidder.Bidder;

/**
 * Defines the contract for any extension that has "prebid" and "bidder" fields.
 * <p>
 * Can be used by {@link Bidder}s to unmarshal any request.imp[i].ext.
 */
@Value(staticConstructor = "of")
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExtImp<P, B> extends FlexibleExtension {

    public static <P, B> ExtImp<P, B> empty() {
        return ExtImp.of(null, null);
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
