package net.jonathangiles.azure.samplesbuilder;

import java.io.File;
import java.io.IOException;

public class Utils {

    public static File createLogFile(Sample sample) throws IOException {
        String outputFileName = sample.getName() + ".txt";
        File logFile = new File(Main.logDir,outputFileName);
        logFile.createNewFile();
        return logFile;
    }
}
