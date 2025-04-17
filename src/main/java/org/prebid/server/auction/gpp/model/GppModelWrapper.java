package org.prebid.server.auction.gpp.model;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.DecodingException;
import com.iab.gpp.encoder.error.EncodingException;
import com.iab.gpp.encoder.section.HeaderV1;
import io.netty.util.collection.IntObjectHashMap;
import io.netty.util.collection.IntObjectMap;

import java.util.List;

public class GppModelWrapper extends GppModel {

    private static final String SECTIONS_DELIMITER = "~";
    private static final String BASE64_PADDING_CHAR = "A";

    private static final int TCF_EU_V2_ID = 2;
    private static final int USP_V1_ID = 6;

    private IntObjectMap<String> sectionIdToEncodedString;

    public GppModelWrapper(String encodedString) throws DecodingException {
        super(padSections(encodedString));
    }

    private static String padSections(String encodedString) {
        final String[] sections = sections(encodedString);

        boolean modified = false;
        for (int i = 1; i < sections.length; i++) {
            final String currentSection = sections[i];
            if (currentSection.length() % 4 > 0 && !currentSection.endsWith("=")) {
                sections[i] = currentSection + BASE64_PADDING_CHAR;
                modified = true;
            }
        }

        return modified
                ? String.join(SECTIONS_DELIMITER, sections)
                : encodedString;
    }

    private static String[] sections(String encodedString) {
        return encodedString.split(SECTIONS_DELIMITER);
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

        final String[] encodedSections = sections(str);
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
