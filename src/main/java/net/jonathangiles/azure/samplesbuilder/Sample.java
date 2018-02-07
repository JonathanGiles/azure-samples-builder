package net.jonathangiles.azure.samplesbuilder;

import org.eclipse.egit.github.core.Repository;

public class Sample {

    private final String url;
    private final String name;
    private boolean cloneSuccess;
    private boolean buildSuccess;

    public Sample(Repository repo) {
        this.url = repo.getCloneUrl();
        this.name = repo.getName();
    }

    public Sample(String url) {
        this.url = url;
        this.name = url.substring(url.lastIndexOf("/") + 1, url.indexOf(".git"));
    }

    public String getUrl() {
        return url;
    }

    public String getName() {
        return name;
    }

    public boolean isCloneSuccessful() {
        return cloneSuccess;
    }

    public void setCloneSuccessful(boolean cloneSuccess) {
        this.cloneSuccess = cloneSuccess;
    }

    public boolean isBuildSuccessful() {
        return buildSuccess;
    }

    public void setBuildSuccessful(boolean buildSuccess) {
        this.buildSuccess = buildSuccess;
    }
}
