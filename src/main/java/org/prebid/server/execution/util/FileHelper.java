package org.prebid.server.execution.util;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;

public class FileHelper {

    public FileOutputStream fromPath(Path path) throws FileNotFoundException {
        return new FileOutputStream(path.toFile());
    }

    public ReadableByteChannel fromUrl(URL url) throws IOException {
        return Channels.newChannel(url.openStream());
    }
}
