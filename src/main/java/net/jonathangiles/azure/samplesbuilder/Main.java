package net.jonathangiles.azure.samplesbuilder;

import org.apache.maven.cli.MavenCli;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Main {
    private final static Logger LOGGER = Logger.getLogger(Main.class.getName());

    private static final File samplesDir = new File("./samples");

    public static void main(String[] args) {
        // TODO delete samples directory so we start clean

        // load sample urls into memory
        List<Sample> samples = loadSampleUrls();
        final int sampleCount = samples.size();

        // start clone / build pipeline
        samples.parallelStream().forEach(sample -> {
            cloneRepo(sample);
            buildSample(sample);
        });

        // upon completion, print report to console
        final long successfulClones = samples.stream().filter(Sample::isCloneSuccessful).count();
        final long successfulBuilds = samples.stream().filter(Sample::isBuildSuccessful).count();

        System.out.println("===========================================================");
        System.out.println("Total samples found: " + sampleCount);
        System.out.println("Total repos successfully cloned: " + successfulClones + " (" + (successfulClones / (float)sampleCount)*100 + "%)");
        System.out.println("Total repos successfully built: " + successfulBuilds + " (" + (successfulBuilds / (float)sampleCount)*100 + "%)");

        if (successfulClones < sampleCount) {
            System.out.println("Repos that could not be cloned: ");
            samples.stream().filter(sample -> !sample.isCloneSuccessful()).forEach(sample -> System.out.println(" - " + sample.getUrl()));
        }

        if (successfulBuilds < sampleCount) {
            System.out.println("Repos that could not be built: ");
            samples.stream().filter(sample -> !sample.isBuildSuccessful()).forEach(sample -> System.out.println(" - " + sample.getUrl()));
        }
        System.out.println("===========================================================");
    }

    private static List<Sample> loadSampleUrls() {
        LOGGER.info("Retrieving all Java-related samples from the azure-samples organization on GitHub");
        try {
            RepositoryService service = new RepositoryService();
            return service.getRepositories("azure-samples")
                    .stream()
                    .filter(repo -> repo.getLanguage() != null && repo.getLanguage().equalsIgnoreCase("java"))
                    .map(Sample::new)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.warning("Unable to connect to github to download samples - aborting");
            e.printStackTrace();
            System.exit(-1);
        }
        return Collections.emptyList();
    }

    private static void cloneRepo(Sample sample) {
        LOGGER.info("Cloning " + sample.getUrl());

        try {
            Git.cloneRepository()
                    .setURI(sample.getUrl())
                    .setDirectory(new File(samplesDir, sample.getName()))
                    .call();
            sample.setCloneSuccessful(true);
        } catch (GitAPIException e) {
            LOGGER.warning("Failed to clone repo " + sample.getUrl());
            e.printStackTrace();
        }
        LOGGER.info("Finished cloning " + sample.getUrl());
    }

    private static void buildSample(Sample sample) {
        LOGGER.info("Building sample " + sample.getName());
        MavenCli cli = new MavenCli();
        int result = cli.doMain(new String[] { "package" }, "./samples/" + sample.getName(), System.out, System.out);
        sample.setBuildSuccessful(result == 1);
        LOGGER.info("Finished building sample " + sample.getName() + " with result " + result);
    }
}
