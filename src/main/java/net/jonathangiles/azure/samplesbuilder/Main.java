package net.jonathangiles.azure.samplesbuilder;

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.maven.cli.MavenCli;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.eclipse.egit.github.core.Authorization;
import org.eclipse.egit.github.core.service.OAuthService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    private final static Logger LOGGER = Logger.getLogger(Main.class.getName());

    private final static ExecutorService buildService = Executors.newSingleThreadExecutor();

    private static final boolean LOAD_LOCAL_SAMPLES = false;

    private static final String githubUserName = System.getenv("username");
    private static final String githubPassword = System.getenv("password");

    public static final File samplesDir = new File("./samples");
    public static final File logDir = new File("./log");
    public static final File logPassDir = new File(logDir, "pass");
    public static final File logFailDir = new File(logDir, "fail");

    public static void main(String[] args) {
        // TODO delete samples directory so we start clean
        logDir.mkdir();
        logPassDir.mkdir();
        logFailDir.mkdir();

        // load sample urls into memory
        List<Sample> samples = LOAD_LOCAL_SAMPLES ? loadLocalSamples() : loadGitHubSamples();
        final int sampleCount = samples.size();
        CountDownLatch latch = new CountDownLatch(sampleCount);

        // start clone / build pipeline. We can clone all repos in parallel, but we
        // can only do one build at a time due to the need for logging output
        samples.parallelStream().forEach(sample -> {
            cloneRepo(sample);
            buildService.submit(() -> {
                sample.getBuildTool().runBuild(sample);
                latch.countDown();
            });
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        buildService.shutdownNow();

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

        // write output to json
        try (Writer writer = new FileWriter("log/results.json")) {
            Gson gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create();
            gson.toJson(samples, writer);
        } catch (IOException e ) {
            e.printStackTrace();
        }

        System.exit(0);
    }

    private static List<Sample> loadLocalSamples() {
        return Stream
                .of(
//                        "https://github.com/Azure-Samples/batch-keyvault-java-management.git",
//                        "https://github.com/Azure-Samples/service-fabric-java-quickstart.git",
//                        "https://github.com/Azure-Samples/storage-java-manage-storage-accounts.git",
                        "https://github.com/Azure-Samples/cognitive-services-android-customvision-sample.git")
                .map(Sample::new)
                .collect(Collectors.toList());
    }

    private static List<Sample> loadGitHubSamples() {
        LOGGER.info("Retrieving all Java-related samples from the azure-samples organization on GitHub");
        try {
            RepositoryService service = new RepositoryService();
            service.getClient().setCredentials(githubUserName, githubPassword);
            return service.getRepositories("azure-samples")
                    .stream()
                    .filter(repo -> repo.getLanguage() != null && repo.getLanguage().equalsIgnoreCase("java"))
                    .map(Sample::new)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.warning("Unable to connect to github to download samples using user name " + githubUserName + " - aborting");
            e.printStackTrace();
            System.exit(-1);
        }
        return Collections.emptyList();
    }

    private static void cloneRepo(Sample sample) {
        LOGGER.info("Cloning " + sample.getUrl());

        try {
            File sampleDir = new File(samplesDir, sample.getName());
            Git.cloneRepository()
                    .setURI(sample.getUrl())
                    .setDirectory(sampleDir)
                    .call();
            sample.setCloneSuccessful(true);

            // check if there is any CI support in this repo (which suggests it is probably supporting
            sample.setCIService(CIService.search(sampleDir));

            // check for the build tool used by this sample
            sample.setBuildTool(BuildTool.search(sampleDir));
        } catch (GitAPIException e) {
            LOGGER.warning("Failed to clone repo " + sample.getUrl());
            e.printStackTrace();
        }
        LOGGER.info("Finished cloning " + sample.getUrl());
    }
}
