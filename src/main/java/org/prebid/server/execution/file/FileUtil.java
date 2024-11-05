package org.prebid.server.execution.file;

import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.FileSystemException;
import org.prebid.server.exception.PreBidException;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtil {

    public static void createAndCheckWritePermissionsFor(FileSystem fileSystem, String filePath) {
        try {
            final Path dirPath = Paths.get(filePath).getParent();
            final String dirPathString = dirPath.toString();
            final FileProps props = fileSystem.existsBlocking(dirPathString)
                    ? fileSystem.propsBlocking(dirPathString)
                    : null;

            if (props == null || !props.isDirectory()) {
                fileSystem.mkdirsBlocking(dirPathString);
            } else if (!Files.isWritable(dirPath)) {
                throw new PreBidException("No write permissions for directory: " + dirPath);
            }
        } catch (FileSystemException | InvalidPathException e) {
            throw new PreBidException("Cannot create directory for file: " + filePath, e);
        }
    }
}
