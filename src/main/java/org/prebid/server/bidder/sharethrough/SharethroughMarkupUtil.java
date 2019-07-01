package org.prebid.server.bidder.sharethrough;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.json.Json;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.sharethrough.model.StrUriParameters;
import org.prebid.server.bidder.sharethrough.model.bidResponse.ExtImpSharethroughResponse;

import java.util.Base64;

class SharethroughMarkupUtil {

    private SharethroughMarkupUtil() {
    }

    static String getAdMarkup(ExtImpSharethroughResponse strResponse, StrUriParameters strUriParameters) {
        StringBuilder tmplBody = new StringBuilder();

        String strRespId = String.format("str_response_%s", strResponse.getBidId());
        String arId = strResponse.getAdserverRequestId();
        String jsonStrResponse = parseResponseToString(strResponse);
        if (StringUtils.isBlank(jsonStrResponse)) {
            return "";
        }

        String encodedJsonResponse = Base64.getEncoder().encodeToString(jsonStrResponse.getBytes());

        tmplBody.append("<img src=\"//b.sharethrough.com/butler?type=s2s-win&arid=").append(arId).append("\" />\n")
                .append("\t\t<div data-str-native-key=\"").append(strUriParameters.getPkey()).append("\" ")
                .append("data-stx-response-name=\"").append(strRespId).append("\"></div>\n")
                .append("\t\t<script>var ").append(strRespId).append(" = ")
                .append("\"").append(encodedJsonResponse).append("\"</script>\n");

        if (strUriParameters.isIframe()) {
            tmplBody.append("\n\t\t<script src=\"//native.sharethrough.com/assets/sfp.js\"></script>\n");
        } else {
            tmplBody.append("\t\t\t<script src=\"//native.sharethrough.com/assets/sfp-set-targeting.js\"></script>\n")
                    .append("\t    \t<script>\n")
                    .append("\t     (function() {\n")
                    .append("\t     if (!(window.STR && window.STR.Tag) && !(window.top.STR && window.top.STR.Tag)){\n")
                    .append("\t         var sfp_js = document.createElement('script');\n")
                    .append("\t         sfp_js.src = \"//native.sharethrough.com/assets/sfp.js\";\n")
                    .append("\t         sfp_js.type = 'text/javascript';\n")
                    .append("\t         sfp_js.charset = 'utf-8';\n")
                    .append("\t         try {\n")
                    .append("\t             window.top.document.getElementsByTagName('body')[0].appendChild(sfp_js);\n")
                    .append("\t         } catch (e) {\n")
                    .append("\t           console.log(e);\n")
                    .append("\t         }\n")
                    .append("\t       }\n")
                    .append("\t     })()\n").append("\t\t   </script>\n");
        }
        return tmplBody.toString();
    }

    private static String parseResponseToString(ExtImpSharethroughResponse response) {
        try {
            return Json.mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL).writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}




