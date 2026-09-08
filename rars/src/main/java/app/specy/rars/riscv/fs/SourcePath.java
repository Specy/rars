package app.specy.rars.riscv.fs;

import java.util.ArrayList;
import java.util.List;

/** Path rules for the virtual source tree used during assembly. */
public final class SourcePath {
    private SourcePath() {
    }

    public static String requireCanonical(String path) {
        if (path == null) {
            throw new IllegalArgumentException("Source path must be a string");
        }
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Source path must not be empty");
        }
        if (path.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Source path must not contain NUL");
        }
        if (path.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Source path must use '/' separators: " + path);
        }
        if (path.startsWith("/") || path.endsWith("/")) {
            throw new IllegalArgumentException("Source path must be root-relative: " + path);
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Source path is not canonical: " + path);
            }
        }
        return path;
    }

    public static String resolveInclude(String includingPath, String includePath) {
        requireCanonical(includingPath);
        if (includePath == null || includePath.isEmpty()) {
            throw new IllegalArgumentException("Include path must not be empty");
        }
        if (includePath.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Include path must not contain NUL");
        }
        if (includePath.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Include path must use '/' separators: " + includePath);
        }

        boolean fromRoot = includePath.startsWith("/");
        String relative = fromRoot ? includePath.substring(1) : includePath;
        if (relative.isEmpty() || relative.endsWith("/") || relative.contains("//")) {
            throw new IllegalArgumentException("Invalid include path: " + includePath);
        }

        List<String> segments = new ArrayList<>();
        if (!fromRoot) {
            int separator = includingPath.lastIndexOf('/');
            if (separator >= 0) {
                for (String segment : includingPath.substring(0, separator).split("/")) {
                    segments.add(segment);
                }
            }
        }

        for (String segment : relative.split("/", -1)) {
            if (segment.isEmpty()) {
                throw new IllegalArgumentException("Invalid include path: " + includePath);
            }
            if (segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    throw new IllegalArgumentException("Include path escapes the virtual root: " + includePath);
                }
                segments.remove(segments.size() - 1);
                continue;
            }
            segments.add(segment);
        }

        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Include path resolves to the virtual root: " + includePath);
        }
        return String.join("/", segments);
    }
}
