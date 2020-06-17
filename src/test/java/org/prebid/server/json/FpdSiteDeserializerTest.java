package org.prebid.server.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Site;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.proto.openrtb.ext.request.ExtBidderConfigFpd;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

public class FpdSiteDeserializerTest extends VertxTest {

    @Test
    public void shouldDeserializeFpdSiteWithUpdatedDomain() throws JsonProcessingException {
        // given
        final ObjectNode extBidderConfigFpdNode = mapper.createObjectNode();
        final JsonNode domain = mapper.createObjectNode().set("domain",
                mapper.createArrayNode().add("domain1.com").add("domain2.com"));
        extBidderConfigFpdNode.set("site", domain);

        // when
        final ExtBidderConfigFpd fpd = mapper.treeToValue(extBidderConfigFpdNode, ExtBidderConfigFpd.class);

        // then
        assertThat(singleton(fpd))
                .extracting(ExtBidderConfigFpd::getSite)
                .extracting(Site::getDomain)
                .containsOnly("domain1.com");
    }

    @Test
    public void shouldDeserializeFpdSiteWithRemovedDomain() throws JsonProcessingException {
        // given
        final ObjectNode extBidderConfigFpdNode = mapper.createObjectNode();
        final JsonNode domain = mapper.createObjectNode().set("domain", mapper.createArrayNode());
        extBidderConfigFpdNode.set("site", domain);

        // when
        final ExtBidderConfigFpd fpd = mapper.treeToValue(extBidderConfigFpdNode, ExtBidderConfigFpd.class);

        // then
        assertThat(singleton(fpd))
                .extracting(ExtBidderConfigFpd::getSite)
                .extracting(Site::getDomain)
                .containsNull();
    }
}
