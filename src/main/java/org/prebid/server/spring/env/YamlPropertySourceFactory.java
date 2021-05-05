package org.prebid.server.spring.env;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    @Nonnull
    public PropertySource<?> createPropertySource(@Nullable String name, @Nonnull EncodedResource resource)
            throws IOException {

        final String sourceName = name != null ? name : resource.getResource().getFilename();
        if (sourceName == null) {
            throw new IllegalArgumentException("Resource does not have a filename");
        }

        final Properties propertiesFromYaml = loadYamlIntoProperties(resource);
        return new PropertiesPropertySource(sourceName, propertiesFromYaml);
    }

    public static Properties readPropertiesFromYamlResource(Resource resource) {
        final YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(resource);
        factory.afterPropertiesSet();

        return factory.getObject();
    }

    private Properties loadYamlIntoProperties(EncodedResource resource) throws FileNotFoundException {
        try {
            return readPropertiesFromYamlResource(resource.getResource());
        } catch (IllegalStateException e) {
            // for ignoreResourceNotFound
            final Throwable cause = e.getCause();
            if (cause instanceof FileNotFoundException) {
                throw (FileNotFoundException) cause;
            }
            throw e;
        }
    }
}
