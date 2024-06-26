package org.prebid.server.activity.infrastructure.creator.privacy.usnat;

import org.junit.jupiter.api.Test;
import org.prebid.server.activity.infrastructure.privacy.PrivacySection;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedCaliforniaGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedColoradoGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedConnecticutGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedUtahGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedVirginiaGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USNationalGppReader;

import static org.assertj.core.api.Assertions.assertThat;

public class USNatGppReaderFactoryTest {

    private final USNatGppReaderFactory target = new USNatGppReaderFactory();

    @Test
    public void fromShouldReturnNationalGppReader() {
        // when and then
        assertThat(target.forSection(PrivacySection.NATIONAL.sectionId(), null))
                .isInstanceOf(USNationalGppReader.class);
    }

    @Test
    public void fromShouldReturnCaliforniaGppReader() {
        // when and then
        assertThat(target.forSection(PrivacySection.CALIFORNIA.sectionId(), null))
                .isInstanceOf(USMappedCaliforniaGppReader.class);
    }

    @Test
    public void fromShouldReturnVirginiaGppReader() {
        // when and then
        assertThat(target.forSection(PrivacySection.VIRGINIA.sectionId(), null))
                .isInstanceOf(USMappedVirginiaGppReader.class);
    }

    @Test
    public void fromShouldReturnColoradoGppReader() {
        // when and then
        assertThat(target.forSection(PrivacySection.COLORADO.sectionId(), null))
                .isInstanceOf(USMappedColoradoGppReader.class);
    }

    @Test
    public void fromShouldReturnUtahGppReader() {
        // when and then
        assertThat(target.forSection(PrivacySection.UTAH.sectionId(), null))
                .isInstanceOf(USMappedUtahGppReader.class);
    }

    @Test
    public void fromShouldReturnConnecticutGppReader() {
        // when and then
        assertThat(target.forSection(PrivacySection.CONNECTICUT.sectionId(), null))
                .isInstanceOf(USMappedConnecticutGppReader.class);
    }
}
