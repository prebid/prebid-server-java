package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;

import java.util.Objects;
import java.util.Set;

public class FpdResolver {

    private static final String USER = "user";
    private static final String SITE = "site";
    private static final String BIDDERS = "bidders";
    private static final String APP = "app";
    private static final String DOOH = "dooh";
    private static final String DEVICE = "device";
    private static final Set<String> KNOWN_FPD_ATTRIBUTES = Set.of(USER, SITE, APP, DOOH, DEVICE, BIDDERS);
    private static final String CONTEXT = "context";
    private static final String DATA = "data";

    private final JacksonMapper jacksonMapper;
    private final JsonMerger jsonMerger;

    public FpdResolver(JacksonMapper jacksonMapper, JsonMerger jsonMerger) {
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
    }

    public User resolveUser(User originUser, ObjectNode fpdUser) {
        return mergeFpd(originUser, fpdUser, User.class);
    }

    public App resolveApp(App originApp, ObjectNode fpdApp) {
        return mergeFpd(originApp, fpdApp, App.class);
    }

    public Device resolveDevice(Device originDevice, ObjectNode fpdDevice) {
        return mergeFpd(originDevice, fpdDevice, Device.class);
    }

    public Site resolveSite(Site originSite, ObjectNode fpdSite) {
        return mergeFpd(originSite, fpdSite, Site.class);
    }

    public Dooh resolveDooh(Dooh originDooh, ObjectNode fpdDooh) {
        return mergeFpd(originDooh, fpdDooh, Dooh.class);
    }

    private <T> T mergeFpd(T original, ObjectNode fpd, Class<T> tClass) {
        if (fpd == null || fpd.isNull() || fpd.isMissingNode()) {
            return original;
        }

        final ObjectMapper mapper = jacksonMapper.mapper();

        final JsonNode originalAsJsonNode = original != null
                ? mapper.valueToTree(original)
                : NullNode.getInstance();
        final JsonNode merged = jsonMerger.merge(fpd, originalAsJsonNode);
        try {
            return mapper.treeToValue(merged, tClass);
        } catch (JsonProcessingException e) {
            throw new InvalidRequestException("Can't convert merging result class " + tClass.getName());
        }
    }

    public ObjectNode resolveImpExt(ObjectNode impExt, ObjectNode targeting) {
        if (targeting == null) {
            return impExt;
        }

        KNOWN_FPD_ATTRIBUTES.forEach(targeting::remove);
        if (!targeting.fieldNames().hasNext()) {
            return impExt;
        }

        if (impExt == null) {
            return jacksonMapper.mapper().createObjectNode()
                    .set(DATA, targeting);
        }

        final JsonNode extImpData = impExt.get(DATA);

        final ObjectNode resolvedData = extImpData != null
                ? (ObjectNode) jsonMerger.merge(targeting, extImpData)
                : targeting;

        return impExt.set(DATA, resolvedData);
    }

    /**
     * @param impExt might be modified within method
     */
    public ObjectNode resolveImpExt(ObjectNode impExt, boolean useFirstPartyData) {
        removeOrReplace(impExt, CONTEXT, sanitizeImpExtContext(impExt, useFirstPartyData));
        removeOrReplace(impExt, DATA, sanitizeImpExtData(impExt, useFirstPartyData));

        return impExt;
    }

    private JsonNode sanitizeImpExtContext(ObjectNode originalImpExt, boolean useFirstPartyData) {
        if (!originalImpExt.hasNonNull(CONTEXT)) {
            return null;
        }

        final JsonNode updatedContextNode = originalImpExt.get(CONTEXT).deepCopy();
        if (!useFirstPartyData && updatedContextNode.hasNonNull(DATA)) {
            ((ObjectNode) updatedContextNode).remove(DATA);
        }

        return updatedContextNode.isObject() && updatedContextNode.isEmpty() ? null : updatedContextNode;
    }

    private JsonNode sanitizeImpExtData(ObjectNode impExt, boolean useFirstPartyData) {
        if (!useFirstPartyData) {
            return null;
        }

        final JsonNode contextNode = impExt.hasNonNull(CONTEXT) ? impExt.get(CONTEXT) : null;
        final JsonNode contextDataNode =
                contextNode != null && contextNode.hasNonNull(DATA) ? contextNode.get(DATA) : null;

        final JsonNode dataNode = impExt.get(DATA);

        final boolean dataIsNullOrObject =
                dataNode == null || dataNode.isObject();
        final boolean contextDataIsObject =
                contextDataNode != null && !contextDataNode.isNull() && contextDataNode.isObject();

        final JsonNode mergedDataNode = dataIsNullOrObject && contextDataIsObject
                ? dataNode != null ? jsonMerger.merge(contextDataNode, dataNode) : contextDataNode
                : dataNode;

        if (mergedDataNode != null && !mergedDataNode.isNull()) {
            return mergedDataNode;
        }

        return null;
    }

    private void removeOrReplace(ObjectNode impExt, String field, JsonNode jsonNode) {
        if (jsonNode == null) {
            impExt.remove(field);
        } else {
            impExt.set(field, jsonNode);
        }
    }
}
