package org.prebid.server.activity.infrastructure.creator.privacy.uscustomlogic;

import org.junit.jupiter.api.Test;
import org.prebid.server.activity.infrastructure.privacy.PrivacySection;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.reader.USCaliforniaGppReader;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.reader.USColoradoGppReader;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.reader.USConnecticutGppReader;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.reader.USUtahGppReader;
import org.prebid.server.activity.infrastructure.privacy.uscustomlogic.reader.USVirginiaGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedCaliforniaGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedColoradoGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedConnecticutGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedUtahGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USMappedVirginiaGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USNationalGppReader;

import static org.assertj.core.api.Assertions.assertThat;

public class USCustomLogicGppReaderFactoryTest {

    private final USCustomLogicGppReaderFactory target = new USCustomLogicGppReaderFactory();

    @Test
    public void fromShouldReturnNationalGppReaderIfNormalizeSectionFalse() {
        // when and then
        assertThat(target.forSection(PrivacySection.NATIONAL.sectionId(), false, null))
                .isInstanceOf(USNationalGppReader.class);
    }

    @Test
    public void fromShouldReturnCaliforniaGppReaderIfNormalizeSectionFalse() {
        // when and then
        assertThat(target.forSection(PrivacySection.CALIFORNIA.sectionId(), false, null))
                .isInstanceOf(USCaliforniaGppReader.class);
    }

    @Test
    public void fromShouldReturnVirginiaGppReaderIfNormalizeSectionFalse() {
        // when and then
        assertThat(target.forSection(PrivacySection.VIRGINIA.sectionId(), false, null))
                .isInstanceOf(USVirginiaGppReader.class);
    }

    @Test
    public void fromShouldReturnColoradoGppReaderIfNormalizeSectionFalse() {
        // when and then
        assertThat(target.forSection(PrivacySection.COLORADO.sectionId(), false, null))
                .isInstanceOf(USColoradoGppReader.class);
    }

    @Test
    public void fromShouldReturnUtahGppReaderIfNormalizeSectionFalse() {
        // when and then
        assertThat(target.forSection(PrivacySection.UTAH.sectionId(), false, null))
                .isInstanceOf(USUtahGppReader.class);
    }

    @Test
    public void fromShouldReturnConnecticutGppReaderIfNormalizeSectionFalse() {
        // when and then
        assertThat(target.forSection(PrivacySection.CONNECTICUT.sectionId(), false, null))
                .isInstanceOf(USConnecticutGppReader.class);
    }

    @Test
    public void fromShouldReturnNationalGppReaderIfNormalizeSectionTrue() {
        // when and then
        assertThat(target.forSection(PrivacySection.NATIONAL.sectionId(), true, null))
                .isInstanceOf(USNationalGppReader.class);
    }

    @Test
    public void fromShouldReturnMappedCaliforniaGppReaderIfNormalizeSectionTrue() {
        // when and then
        assertThat(target.forSection(PrivacySection.CALIFORNIA.sectionId(), true, null))
                .isInstanceOf(USMappedCaliforniaGppReader.class);
    }

    @Test
    public void fromShouldReturnMappedVirginiaGppReaderIfNormalizeSectionTrue() {
        // when and then
        assertThat(target.forSection(PrivacySection.VIRGINIA.sectionId(), true, null))
                .isInstanceOf(USMappedVirginiaGppReader.class);
    }

    @Test
    public void fromShouldReturnMappedColoradoGppReaderIfNormalizeSectionTrue() {
        // when and then
        assertThat(target.forSection(PrivacySection.COLORADO.sectionId(), true, null))
                .isInstanceOf(USMappedColoradoGppReader.class);
    }

    @Test
    public void fromShouldReturnMappedUtahGppReaderIfNormalizeSectionTrue() {
        // when and then
        assertThat(target.forSection(PrivacySection.UTAH.sectionId(), true, null))
                .isInstanceOf(USMappedUtahGppReader.class);
    }

    @Test
    public void fromShouldReturnMappedConnecticutGppReaderIfNormalizeSectionTrue() {
        // when and then
        assertThat(target.forSection(PrivacySection.CONNECTICUT.sectionId(), true, null))
                .isInstanceOf(USMappedConnecticutGppReader.class);
    }
}
