package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary;

import lombok.Builder;

import java.util.Collection;
import java.util.Map;

/**
 * A set of information pieces that module can use for
 * detecting missing {@link com.iab.openrtb.request.Device} properties.
 *
 * @param rawHeaders Raw HTTP Headers obtained at Entrypoint stage.
 * @param deviceUA User-Agent string from {@link com.iab.openrtb.request.Device#getUa()}.
 * @param secureHeaders Reconstructed HTTP Headers from {@link com.iab.openrtb.request.Device#getSua()}.
 */
@Builder(toBuilder = true)
public record CollectedEvidence(
        Collection<Map.Entry<String, String>> rawHeaders,
        String deviceUA,
        Map<String, String> secureHeaders
) {
}
