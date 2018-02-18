package net.jonathangiles.azure.samplesbuilder;

import com.google.common.io.Files;
import org.apache.maven.cli.MavenCli;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.gradle.tooling.*;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Logger;

public enum BuildTool {

    NONE("None", null) {
        @Override public int runBuild(Sample sample) {
            return BUILD_FAILURE;
        }
    },
    MAVEN("Maven", "pom.xml") {
        @Override public int runBuild(Sample sample) {
            LOGGER.info("Building Maven sample " + sample.getName());
            int result = BUILD_FAILURE;
            try {
                File logFile = Utils.createLogFile(sample);
                PrintStream stdout = new PrintStream(logFile);

                final ClassWorld classWorld = new ClassWorld("plexus.core", Main.class.getClassLoader());
                MavenCli cli = new MavenCli(classWorld);
                result = cli.doMain(new String[]{"package"}, "./samples/" + sample.getName(), stdout, stdout);

                finaliseSample(sample, logFile, result == 0);

                LOGGER.info("Finished building Maven sample " + sample.getName() + " with result " + result);
            } catch (Exception e) {
                LOGGER.info("Failed to build Maven sample " + sample.getName());
                e.printStackTrace();
            }

            return result;
        }
    },
    GRADLE("Gradle", "build.gradle") {
        @Override public int runBuild(Sample sample) {
            ProjectConnection connection = GradleConnector.newConnector()
                    .forProjectDirectory(new File("./samples/" + sample.getName()))
                    .connect();

            try {
                File logFile = Utils.createLogFile(sample);
                PrintStream stdout = new PrintStream(logFile);

                BuildLauncher build = connection.newBuild();
                build.forTasks("assemble");
                build.setStandardOutput(stdout);
                build.setStandardError(stdout);
                build.run(new ResultHandler<Void>() {
                    @Override
                    public void onComplete(Void aVoid) {
                        finaliseSample(sample, logFile, true);
                    }

                    @Override
                    public void onFailure(GradleConnectionException e) {
                        finaliseSample(sample, logFile, false);
                    }
                });
            } catch (Exception e) {
                LOGGER.info("Failed to build Gradle sample " + sample.getName());
                e.printStackTrace();
            } finally {
                connection.close();
            }

            return BUILD_FAILURE;
        }
    };

    private final static Logger LOGGER = Logger.getLogger(BuildTool.class.getName());

    public static final int BUILD_SUCCESS = 0;
    public static final int BUILD_FAILURE = 1;

    private String displayName;
    private String filename;

    BuildTool(String displayName, String filename) {
        this.displayName = displayName;
        this.filename = filename;
    }

    public abstract int runBuild(Sample sample);

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static BuildTool search(File searchDir) {
        if (new File(searchDir, MAVEN.filename).exists()) {
            return MAVEN;
        } else if (new File(searchDir, GRADLE.filename).exists()) {
            return GRADLE;
        }
        return NONE;
    }

    void finaliseSample(Sample sample, File logFile, boolean success) {
        try {
            sample.setBuildSuccessful(success);
            Files.move(logFile, new File(success ? Main.logPassDir : Main.logFailDir, logFile.getName()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
