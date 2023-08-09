package org.prebid.server.activity.infrastructure.creator.privacy.usnat;

import org.junit.Test;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USCaliforniaGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USColoradoGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USConnecticutGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USNationalGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USUtahGppReader;
import org.prebid.server.activity.infrastructure.privacy.usnat.reader.USVirginiaGppReader;

import static org.assertj.core.api.Assertions.assertThat;

public class USNatGppReaderFactoryTest {

    private final USNatGppReaderFactory target = new USNatGppReaderFactory();

    @Test
    public void fromShouldReturnNationalGppReader() {
        // when and then
        assertThat(target.forSection(USNatSection.NATIONAL.sectionId(), null))
                .isInstanceOf(USNationalGppReader.class);
    }

    @Test
    public void fromShouldReturnCaliforniaGppReader() {
        // when and then
        assertThat(target.forSection(USNatSection.CALIFORNIA.sectionId(), null))
                .isInstanceOf(USCaliforniaGppReader.class);
    }

    @Test
    public void fromShouldReturnVirginiaGppReader() {
        // when and then
        assertThat(target.forSection(USNatSection.VIRGINIA.sectionId(), null))
                .isInstanceOf(USVirginiaGppReader.class);
    }

    @Test
    public void fromShouldReturnColoradoGppReader() {
        // when and then
        assertThat(target.forSection(USNatSection.COLORADO.sectionId(), null))
                .isInstanceOf(USColoradoGppReader.class);
    }

    @Test
    public void fromShouldReturnUtahGppReader() {
        // when and then
        assertThat(target.forSection(USNatSection.UTAH.sectionId(), null))
                .isInstanceOf(USUtahGppReader.class);
    }

    @Test
    public void fromShouldReturnConnecticutGppReader() {
        // when and then
        assertThat(target.forSection(USNatSection.CONNECTICUT.sectionId(), null))
                .isInstanceOf(USConnecticutGppReader.class);
    }
}
