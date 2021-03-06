package com.jfrog.ide.idea.scan;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.jfrog.ide.common.utils.PackageFileFinder;
import com.jfrog.ide.idea.projects.GoProject;
import com.jfrog.ide.idea.projects.NpmProject;
import com.jfrog.ide.idea.configuration.GlobalSettings;
import com.jfrog.ide.idea.log.Logger;
import com.jfrog.ide.idea.ui.issues.IssuesTree;
import com.jfrog.ide.idea.ui.licenses.LicensesTree;
import com.jfrog.ide.idea.utils.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Created by yahavi
 */
public class ScanManagersFactory {

    private Map<Integer, ScanManager> scanManagers = Maps.newHashMap();
    private Project mainProject;

    public static ScanManagersFactory getInstance(@NotNull Project project) {
        return ServiceManager.getService(project, ScanManagersFactory.class);
    }

    private ScanManagersFactory(@NotNull Project project) {
        this.mainProject = project;
    }

    public static Set<ScanManager> getScanManagers(@NotNull Project project) {
        ScanManagersFactory scanManagersFactory = getInstance(project);
        return Sets.newHashSet(scanManagersFactory.scanManagers.values());
    }

    /**
     * Start an Xray scan for all projects.
     *
     * @param quickScan           - True to allow usage of the scan cache.
     * @param libraryDependencies - Dependencies to use in Gradle scans.
     */
    public void startScan(boolean quickScan, @Nullable Collection<DataNode<LibraryDependencyData>> libraryDependencies) {
        if (DumbService.isDumb(mainProject)) { // If intellij is still indexing the project
            return;
        }
        if (isScanInProgress()) {
            Logger.getInstance(mainProject).info("Previous scan still running...");
            return;
        }
        if (!GlobalSettings.getInstance().areCredentialsSet()) {
            Logger.getInstance(mainProject).error("Xray server is not configured.");
            return;
        }
        try {
            IssuesTree issuesTree = IssuesTree.getInstance(mainProject);
            LicensesTree licensesTree = LicensesTree.getInstance(mainProject);
            if (issuesTree == null || licensesTree == null) {
                return;
            }
            refreshScanManagers();
            resetViews(issuesTree, licensesTree);
            for (ScanManager scanManager : scanManagers.values()) {
                scanManager.asyncScanAndUpdateResults(quickScan, libraryDependencies);
            }
        } catch (IOException | RuntimeException e) {
            Logger.getInstance(mainProject).error("", e);
        }
    }

    /**
     * Start an Xray scan after Gradle dependencies import.
     * For known Gradle projects - Start scan only for the project.
     * For new Gradle projects - Start a full scan.
     *
     * @param project             - The Gradle project
     * @param libraryDependencies - Gradle's dependencies
     */
    public void tryScanSingleProject(Project project, Collection<DataNode<LibraryDependencyData>> libraryDependencies) {
        ScanManager scanManager = scanManagers.get(Utils.getProjectIdentifier(project));
        if (scanManager != null) { // If Gradle project already exists
            scanManager.asyncScanAndUpdateResults(true, libraryDependencies);
            return;
        }
        startScan(true, libraryDependencies); // New Gradle project
    }

    /**
     * Scan for Maven, Gradle and Npm projects. Create new ScanManagers and delete unnecessary ones.
     */
    public void refreshScanManagers() throws IOException {
        Map<Integer, ScanManager> scanManagers = Maps.newHashMap();
        final Set<Path> paths = Sets.newHashSet();
        int projectHash = Utils.getProjectIdentifier(mainProject);
        ScanManager scanManager = this.scanManagers.get(projectHash);
        if (scanManager != null) {
            scanManagers.put(projectHash, scanManager);
        } else {
            if (MavenScanManager.isApplicable(mainProject)) {
                scanManagers.put(projectHash, createScanManager(ScanManagerTypes.MAVEN, mainProject, ""));
            }
            if (GradleScanManager.isApplicable(mainProject)) {
                scanManagers.put(projectHash, createScanManager(ScanManagerTypes.GRADLE, mainProject, ""));
            }
        }
        paths.add(Utils.getProjectBasePath(mainProject));
        createScanManagers(scanManagers, paths);
        this.scanManagers = scanManagers;
    }

    private void createScanManagers(Map<Integer, ScanManager> scanManagers, Set<Path> paths) throws IOException {
        scanManagers.values().stream().map(ScanManager::getProjectPaths).flatMap(Collection::stream).forEach(paths::add);
        PackageFileFinder packageFileFinder = new PackageFileFinder(paths, GlobalSettings.getInstance().getXrayConfig().getExcludedPaths());

        // Create npm scan-managers.
        Set<String> packageJsonDirs = packageFileFinder.getNpmPackagesFilePairs();
        createScanManagersForPackageDirs(packageJsonDirs, scanManagers, ScanManagerTypes.NPM);

        // Create go scan-managers.
        Set<String> gomodDirs = packageFileFinder.getGoPackagesFilePairs();
        createScanManagersForPackageDirs(gomodDirs, scanManagers, ScanManagerTypes.GO);
    }

    private void createScanManagersForPackageDirs(Set<String> packageDirs, Map<Integer, ScanManager> scanManagers,
                                                  ScanManagerTypes type) throws IOException {
        for (String dir : packageDirs) {
            int projectHash = Utils.getProjectIdentifier(dir, dir);
            ScanManager scanManager = scanManagers.get(projectHash);
            if (scanManager != null) {
                scanManagers.put(projectHash, scanManager);
            } else {
                scanManagers.put(projectHash, createScanManager(type, mainProject, dir));
            }
        }
    }

    private enum ScanManagerTypes {
        MAVEN,
        GRADLE,
        NPM,
        GO
    }

    private ScanManager createScanManager(ScanManagerTypes type, Project project, String dir) throws IOException {
        switch (type) {
            case MAVEN:
                return new MavenScanManager(project);
            case GRADLE:
                return new GradleScanManager(project);
            case NPM:
                return new NpmScanManager(project, new NpmProject(project.getBaseDir(), dir));
            case GO:
                return new GoScanManager(project, new GoProject(project.getBaseDir(), dir));
        }
        throw new IOException("Invalid scan-manager type provided.");
    }

    private boolean isScanInProgress() {
        return scanManagers.values().stream().anyMatch(ScanManager::isScanInProgress);
    }

    private void resetViews(IssuesTree issuesTree, LicensesTree licensesTree) {
        issuesTree.reset();
        licensesTree.reset();
    }
}
