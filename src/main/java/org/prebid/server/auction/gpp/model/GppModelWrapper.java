package org.prebid.server.auction.gpp.model;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.DecodingException;
import com.iab.gpp.encoder.error.EncodingException;
import com.iab.gpp.encoder.section.HeaderV1;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

import java.util.List;

public class GppModelWrapper extends GppModel {

    private static final int TCF_EU_V2_ID = 2;
    private static final int USP_V1_ID = 6;

    private IntObjectMap<String> sectionIdToEncodedString;

    public GppModelWrapper(String encodedString) throws DecodingException {
        super(padSections(encodedString));
    }

    private static String padSections(String gpp) {
        final StringBuilder gppBuilder = new StringBuilder(gpp);

        int subsectionStart = 0;
        int offset = 0;
        for (int i = 1; i < gpp.length(); i++) {
            final char currentChar = gpp.charAt(i);

            if (currentChar == '~' || currentChar == '.') {
                if ((i - subsectionStart) % 4 != 0 && gpp.charAt(i - 1) != '=') {
                    gppBuilder.insert(i + offset, "A");
                    offset++;
                }

                subsectionStart = i + 1;
            }
        }

        final int lastSubsectionLength = gpp.length() - subsectionStart;
        if (lastSubsectionLength > 0 && lastSubsectionLength % 4 != 0 && !gpp.endsWith("=")) {
            gppBuilder.append("A");
        }

        return gppBuilder.toString();
    }

    private void init() {
        if (sectionIdToEncodedString == null) {
            sectionIdToEncodedString = new IntObjectHashMap<>();
        }
        sectionIdToEncodedString.clear();
    }

    @Override
    public void decode(String str) throws DecodingException {
        super.decode(str);
        init();

        final String[] encodedSections = str.split("~");
        final List<Integer> sectionIds = ((HeaderV1) getSection(HeaderV1.NAME)).getSectionsIds();

        for (int i = 0; i < sectionIds.size(); i++) {
            switch (sectionIds.get(i)) {
                case TCF_EU_V2_ID -> sectionIdToEncodedString.put(TCF_EU_V2_ID, encodedSections[i + 1]);
                case USP_V1_ID -> sectionIdToEncodedString.put(USP_V1_ID, encodedSections[i + 1]);
            }
        }
    }

    @Override
    public String encodeSection(int sectionId) throws EncodingException {
        final String originalSectionString = sectionIdToEncodedString.get(sectionId);
        return originalSectionString != null
                ? originalSectionString
                : super.encodeSection(sectionId);
    }
}
