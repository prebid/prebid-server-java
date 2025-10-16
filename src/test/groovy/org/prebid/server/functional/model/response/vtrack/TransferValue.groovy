package org.prebid.server.functional.model.response.vtrack

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class TransferValue {

    String adm
    Integer width
    Integer height

    static final TransferValue getTransferValue(){
        return new TransferValue().tap {
            adm = "<script type=\\\"text/javascript\\\">\\n      rubicon_cb = Math.random(); rubicon_rurl = document.referrer; if(top.location==document.location){rubicon_rurl = document.location;} rubicon_rurl = escape(rubicon_rurl);\\n      window.rubicon_ad = \\\"4073548\\\" + \\\".\\\" + \\\"js\\\";\\n      window.rubicon_creative = \\\"4458534\\\" + \\\".\\\" + \\\"js\\\";\\n    </script>\\n<div style=\\\"width: 0; height: 0; overflow: hidden;\\\"><img border=\\\"0\\\" width=\\\"1\\\" height=\\\"1\\\" src=\\\"http://beacon-us-iad2.rubiconproject.com/beacon/d/f5b45196-4d05-4e42-8190-264d993c3515?accountId=1001&siteId=113932&zoneId=535510&e=6A1E40E384DA563BD667C48E6BE5FF2436D13A174DE937CDDAAF548B67FBAEBDC779E539A868F72E270E87E31888912083DA7E4E4D5AF24E782EF9778EE5B34E9F0ADFB8523971184242CC624DE62CD4BB342D372FA82497B63ADB685D502967FCB404AD24048D03AFEA50BAA8A987A017B93F2D2A1C5933B4A7786F3B6CF76724F5207A2458AD77E82A954C1004678A\\\" alt=\\\"\\\" /></div>\\n\\n\\n<a href=\\\"http://optimized-by.rubiconproject.com/t/1001/113932/535510-15.4073548.4458534?url=http%3A%2F%2Frubiconproject.com\\\" target=\\\"_blank\\\"><img src=\\\"https://secure-assets.rubiconproject.com/campaigns/1001/50/59/48/1476242257campaign_file_q06ab2.png\\\" border=\\\"0\\\" alt=\\\"\\\" /></a><div style=\\\"height:0px;width:0px;overflow:hidden\\\"><script>(function(){document.write('<iframe src=\\\"https://tap2-cdn.rubiconproject.com/partner/scripts/rubicon/emily.html?pc=1001/113932&geo=na&co=us\\\" frameborder=\\\"0\\\" marginwidth=\\\"0\\\" marginheight=\\\"0\\\" scrolling=\\\"NO\\\" width=\\\"0\\\" height=\\\"0\\\" style=\\\"height:0px;width:0px\\\"></iframe>');})();</script></div>\\n\",\n"
            width = 300
            height = 250
        }
    }
}
