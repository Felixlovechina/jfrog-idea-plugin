package com.jfrog.ide.idea.ui.licenses;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.util.ui.UIUtil;
import com.jfrog.ide.idea.configuration.GlobalSettings;
import com.jfrog.ide.idea.ui.JFrogToolWindow;
import com.jfrog.ide.idea.ui.components.FilterButton;
import com.jfrog.ide.idea.ui.components.TitledPane;
import com.jfrog.ide.idea.ui.filters.LicenseFilterMenu;
import com.jfrog.ide.idea.ui.utils.ComponentUtils;
import org.jetbrains.annotations.NotNull;
import org.jfrog.build.extractor.scan.DependenciesTree;

import javax.swing.*;
import java.awt.*;

/**
 * @author yahavi
 */
public class LicensesTab {

    private OnePixelSplitter licensesCentralVerticalSplit;
    private JScrollPane licensesDetailsScroll;
    private JPanel licensesDetailsPanel;
    private LicensesTree licensesTree;
    private Project mainProject;

    /**
     * @param mainProject - Currently opened IntelliJ project
     * @param supported   - True if the current opened project is supported by the plugin.
     *                    If now, show the "Unsupported project type" message.
     * @return the licenses view panel
     */
    public JPanel createLicenseInfoTab(@NotNull Project mainProject, boolean supported) {
        this.mainProject = mainProject;
        this.licensesTree = LicensesTree.getInstance(mainProject);
        LicenseFilterMenu licenseFilterMenu = new LicenseFilterMenu(mainProject);
        JPanel licensesFilterButton = new FilterButton(licenseFilterMenu, "License", "Select licenses to show");
        JPanel toolbar = ComponentUtils.createActionToolbar("Licenses toolbar", licensesFilterButton, licensesTree);

        licensesTree.setLicenseFilterMenu(licenseFilterMenu);

        licensesCentralVerticalSplit = new OnePixelSplitter(false, 0.3f);
        licensesCentralVerticalSplit.setFirstComponent(createLicensesComponentsTreeView());
        licensesCentralVerticalSplit.setSecondComponent(createLicenseDetailsView(supported));

        OnePixelSplitter licenseTab = new OnePixelSplitter(true, 0f);
        licenseTab.setResizeEnabled(false);
        licenseTab.setFirstComponent(toolbar);
        licenseTab.setSecondComponent(licensesCentralVerticalSplit);
        return licenseTab;
    }

    /**
     * Create the licenses details panel. That is the right licence details panel.
     *
     * @return the licenses details panel
     */
    private JComponent createLicenseDetailsView(boolean supported) {
        if (!GlobalSettings.getInstance().areCredentialsSet()) {
            return ComponentUtils.createNoCredentialsView();
        }
        JLabel title = new JBLabel(" Details");
        title.setFont(title.getFont().deriveFont(JFrogToolWindow.TITLE_FONT_SIZE));

        licensesDetailsPanel = new JBPanel(new BorderLayout()).withBackground(UIUtil.getTableBackground());
        String panelText = supported ? ComponentUtils.SELECT_COMPONENT_TEXT : ComponentUtils.UNSUPPORTED_TEXT;
        licensesDetailsPanel.add(ComponentUtils.createDisabledTextLabel(panelText), BorderLayout.CENTER);
        licensesDetailsScroll = ScrollPaneFactory.createScrollPane(licensesDetailsPanel, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        return new TitledPane(JSplitPane.VERTICAL_SPLIT, JFrogToolWindow.TITLE_LABEL_SIZE, title, licensesDetailsScroll);
    }

    /**
     * Create the licenses tree panel.
     *
     * @return the licenses tree panel
     */
    private JComponent createLicensesComponentsTreeView() {
        JPanel componentsTreePanel = new JBPanel(new BorderLayout());
        componentsTreePanel.setBackground(UIUtil.getTableBackground());
        JLabel componentsTreeTitle = new JBLabel(" Components Tree");
        componentsTreeTitle.setFont(componentsTreeTitle.getFont().deriveFont(JFrogToolWindow.TITLE_FONT_SIZE));
        componentsTreePanel.add(componentsTreeTitle, BorderLayout.WEST);

        TreeSpeedSearch treeSpeedSearch = new TreeSpeedSearch(licensesTree, ComponentUtils::getPathSearchString, true);
        JScrollPane treeScrollPane = ScrollPaneFactory.createScrollPane(treeSpeedSearch.getComponent());
        treeScrollPane.getVerticalScrollBar().setUnitIncrement(JFrogToolWindow.SCROLL_BAR_SCROLLING_UNITS);
        return new TitledPane(JSplitPane.VERTICAL_SPLIT, JFrogToolWindow.TITLE_LABEL_SIZE, componentsTreePanel, treeScrollPane);
    }

    /**
     * Called after a change in the credentials.
     */
    public void onConfigurationChange() {
        licensesCentralVerticalSplit.setSecondComponent(createLicenseDetailsView(true));
    }

    /**
     * Register the licenses tree listeners.
     */
    public void registerListeners() {
        // License component selection listener
        licensesTree.addTreeSelectionListener(e -> {
            if (e == null || e.getNewLeadSelectionPath() == null) {
                return;
            }
            ComponentLicenseDetails.createLicenseDetailsView(licensesDetailsPanel, (DependenciesTree) e.getNewLeadSelectionPath().getLastPathComponent());

            // Scroll back to the beginning of the scrollable panel
            ApplicationManager.getApplication().invokeLater(() -> licensesDetailsScroll.getViewport().setViewPosition(new Point()));
        });

        licensesTree.addOnProjectChangeListener(mainProject.getMessageBus().connect());
    }
}
