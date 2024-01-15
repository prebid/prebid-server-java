package org.prebid.server.auction.gpp.model;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.DecodingException;
import com.iab.gpp.encoder.error.EncodingException;
import com.iab.gpp.encoder.error.InvalidFieldException;
import com.iab.gpp.encoder.section.HeaderV1;
import com.iab.gpp.encoder.section.TcfEuV2;
import com.iab.gpp.encoder.section.UspV1;
import org.junit.Test;

import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

public class GppModelWrapperTest {

    private static final String GPP_STRING = "DBACNYA~"
            // TCF
            + "CPc4uOkP3So0AD2ADBENAgEgAP_AAEPAAAAAg1NX_H__bW9r8Xp3aft0eY1P99jz7sQxBhfJE-4FyLvW_JwXw2EwNA26pqIKmRIE"
            + "u1JBIQFlGJHUBVigaogVryHsYkGcgSNKJ6BEgFMRM2dYCF5OmQtj-QKY5vp9d3bx2D-t7dv83dTzz8VHn3eZfmckUICdQ58tDfn9"
            + "bRKb85IKd_78v4v09F_rk2_eTVn_pcvr7B8uft87_XU-9_ffAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
            + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEQaoaQACIAFAAXAA4AD4AKAAqABcADgAHgAQAAkgBcAGUANAA1AB4AD8AIgARw"
            + "AmABQgCkAKYAVYAtgC6AGIAMwAaAA3gB6AD8AIQAQ0AiACJAEcAJYATQAnABRgDAAGHAMoAywBmgDRAGyAOQAc8A7gDvAHsAPiAf"
            + "YB-wD_AQCAg4CEAERAIpARYBGACNQEcAR0AkQBJQCUgE0AJ2AT8AoMBUAFRAKuAWIAuYBdYC8gL0AX0AxQBogDXgG0ANwAcQA44B"
            + "0gDqAHbAPaAfYA_4CJgEXgI9gSIBIoCVAErAJigTIBMoCbQE7AKHgUeBSICk4FNAU2Ap8BUMCpAKlAVUAqwBXICuwFhQLEAsUBZQ"
            + "C0QFqQLYAtmBbgFugLgAXIAugBdoC74F5AXmAvoBf4DBAGDAMNAYgAxYBjwDIYGRgZJAyYDJwGVAMsAZmAzkBngDRAGjANNAamA1"
            + "WBq4GsgNeAbQA2yBtwG3wNzA3UBwADggHFgOPAcnA5YDlwHOgOfAdYA8UB48DyQPKAfFA-QD5QH0gPrgfaB90D9gP3Af2BAECAgE"
            + "DAIHgQRAgmBBgCDYEIQIUAQrghaCFwEM4Icgh1BDwEPQIfgRTAjABGkCNYEbwI4gR0AjsBHsCPoEfwJCASKAkbBJAEkoJMAkzBKg"
            + "EqQJYASzgluCXEEugS7Al9BMAEwQJhgTFgmYCZwE1AJsQTbBNyCbwJvgThgnKCcwE6Qg1AAA~"
            // USP
            + "1YN-";

    @Test
    public void test() throws DecodingException, EncodingException, InvalidFieldException {
        // given and when
        final GppModel originalGpp = new GppModel(GPP_STRING);
        final GppModel wrappedGpp = new GppModelWrapper(GPP_STRING);

        // then
        assertThat(wrappedGpp.encodeSection(HeaderV1.ID)).isEqualTo(originalGpp.encodeSection(HeaderV1.ID));
        assertThat(wrappedGpp.encodeSection(TcfEuV2.ID))
                .usingComparator(Comparator.comparing(GppModelWrapperTest::encodedTcfEuV2SectionNormalizer))
                .isEqualTo(originalGpp.encodeSection(TcfEuV2.ID));
        assertThat(wrappedGpp.encodeSection(UspV1.ID)).isEqualTo(originalGpp.encodeSection(UspV1.ID));
    }

    public static String encodedTcfEuV2SectionNormalizer(String encodedSection) {
        try {
            final GppModel tcfComparator = new GppModel();
            tcfComparator.decodeSection(TcfEuV2.ID, encodedSection);
            return tcfComparator.encodeSection(TcfEuV2.ID);
        } catch (DecodingException | EncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
