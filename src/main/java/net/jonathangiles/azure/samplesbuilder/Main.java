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

    private static final String githubUserName = System.getProperty("username");
    private static final String githubPassword = System.getProperty("password");

    private static final File samplesDir = new File("./samples");
    private static final File logDir = new File("./log");
    private static final File logPassDir = new File(logDir, "pass");
    private static final File logFailDir = new File(logDir, "fail");

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
                buildSample(sample);
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
                        "https://github.com/Azure-Samples/batch-keyvault-java-management.git",
                        "https://github.com/Azure-Samples/service-fabric-java-quickstart.git",
                        "https://github.com/Azure-Samples/storage-java-manage-storage-accounts.git")
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

        try {
            String outputFileName = sample.getName() + ".txt";
            File logFile = new File(logDir,outputFileName);
            logFile.createNewFile();
            PrintStream stdout = new PrintStream(logFile);

            final ClassWorld classWorld = new ClassWorld("plexus.core", Main.class.getClassLoader());
            MavenCli cli = new MavenCli(classWorld);
            int result = cli.doMain(new String[]{"package"}, "./samples/" + sample.getName(), stdout, stdout);

            boolean success = result == 0;
            sample.setBuildSuccessful(success);

            Files.move(logFile, new File(success ? logPassDir : logFailDir, outputFileName));

            LOGGER.info("Finished building sample " + sample.getName() + " with result " + result);
        } catch (Exception e) {
            LOGGER.info("Failed to build sample " + sample.getName());
            e.printStackTrace();
        }
    }
}
