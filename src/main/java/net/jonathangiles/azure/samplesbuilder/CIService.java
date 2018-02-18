package net.jonathangiles.azure.samplesbuilder;

import java.io.File;

public enum CIService {

    NONE("None", null),
    TRAVIS("Travis CI", ".travis.yml");

    private final String displayName;
    private final String filename;

    CIService(String displayName, String filename) {
        this.displayName = displayName;
        this.filename = filename;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFilename() {
        return filename;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static CIService search(File searchDir) {
        if (new File(searchDir, TRAVIS.filename).exists()) {
            return TRAVIS;
        }
        return NONE;
    }
}
