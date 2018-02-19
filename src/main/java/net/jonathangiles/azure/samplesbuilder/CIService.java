package net.jonathangiles.azure.samplesbuilder;

import com.google.gson.annotations.SerializedName;

import java.io.File;

public enum CIService {

    @SerializedName("None")
    NONE(null),

    @SerializedName("TravisCI")
    TRAVIS(".travis.yml");

    private final String filename;

    CIService(String filename) {
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }

    public static CIService search(File searchDir) {
        if (new File(searchDir, TRAVIS.filename).exists()) {
            return TRAVIS;
        }
        return NONE;
    }
}
