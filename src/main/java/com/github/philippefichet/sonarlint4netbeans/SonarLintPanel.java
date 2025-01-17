/*
 * sonarlint4netbeans: SonarLint integration for Apache Netbeans
 * Copyright (C) 2020 Philippe FICHET.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package com.github.philippefichet.sonarlint4netbeans;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import org.openide.util.Lookup;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.Version;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;

public final class SonarLintPanel extends javax.swing.JPanel {

    private final SonarLintOptionsPanelController controller;

    private final Map<RuleKey, Boolean> ruleKeyChanged = new HashMap<>();
    private String nodeJSPathToSave;
    private Version nodeJSVersionToSave;
    private Boolean applyDifferentRulesOnTestFiles = null;
    private DefaultTableModel analyzerDefaultTableModel = new DefaultTableModel();

    private SonarLintRuleTableModel rulesDefaultTableModel = new SonarLintRuleTableModel();

    public SonarLintPanel(SonarLintOptionsPanelController controller) {
        this.controller = controller;
        initComponents();

        JPanel loadingPanel = new JPanel();
        loadingPanel.setLayout(new BoxLayout(loadingPanel, BoxLayout.PAGE_AXIS));
        JProgressBar waiting = new JProgressBar();
        waiting.setIndeterminate(true);
        JLabel loadingText = new JLabel("Loading ...");
        loadingPanel.add(loadingText);
        loadingPanel.add(waiting);
        optionPanel.add(loadingPanel, BorderLayout.NORTH);

        SonarLintEngine sonarLintEngine = Lookup.getDefault().lookup(SonarLintEngine.class);
        sonarLintEngine.whenInitialized(engine -> {
            analyzerDefaultTableModel.addColumn("Key");
            analyzerDefaultTableModel.addColumn("Name");
            analyzerDefaultTableModel.addColumn("Version");
            analyzerDefaultTableModel.addColumn("Status");
            Collection<PluginDetails> loadedAnalyzers = engine.getPluginDetails();
            for (PluginDetails loadedAnalyzer : loadedAnalyzers) {
                analyzerDefaultTableModel.addRow(new Object[]{
                    loadedAnalyzer.key(),
                    loadedAnalyzer.name(),
                    loadedAnalyzer.version(),
                    loadedAnalyzer.skipReason().map(reason -> "Disable: " + reason.toString()).orElse("Enable")
                });
            }

            rulesDefaultTableModel.addTableModelListener(e -> {
                controller.changed();
                int column = e.getColumn();

                if (column == 0) {
                    int firstRow = e.getFirstRow();
                    RuleKey ruleKey = RuleKey.parse(
                        rulesDefaultTableModel.getRuleKeyValueAt(firstRow).toString()
                    );
                    Object valueAt = rulesDefaultTableModel.getValueAt(firstRow, column);
                    ruleKeyChanged.put(ruleKey, (Boolean) valueAt);
                }
            });

            categoriesList.addListSelectionListener((e) -> {
                if ("Rules".equals(categoriesList.getSelectedValue())) {
                    initRulesPanel(engine);
                }
                if ("Analyzers".equals(categoriesList.getSelectedValue())) {
                    initAnalyzersPanel();
                }
                if ("Options".equals(categoriesList.getSelectedValue())) {
                    initOptionsPanel(engine);
                }
                optionPanel.revalidate();
                optionPanel.repaint();
            });

            // Rule panel by default
            initRulesPanel(engine);
            optionPanel.revalidate();
            optionPanel.repaint();
        });

    }

    private void initAnalyzersPanel() {
        optionPanel.removeAll();
        JTable analyzersTable = new JTable(analyzerDefaultTableModel);
        optionPanel.add(analyzersTable.getTableHeader(), BorderLayout.NORTH);
        optionPanel.add(analyzersTable, BorderLayout.CENTER);
    }

    private void initOptionsPanel(SonarLintEngine engine) {
        optionPanel.removeAll();
        SonarLintOptionsPanelOptions container = new SonarLintOptionsPanelOptions(engine, new SonarLintOptionsPanelOptionsListener() {
            @Override
            public void nodeJSOptionsChanged(String nodeJSPath, Version nodeJSVersion) {
                nodeJSPathToSave = nodeJSPath;
                nodeJSVersionToSave = nodeJSVersion;
                controller.changed();
            }

            @Override
            public void testRulesOptionsChanged(Boolean apply) {
                applyDifferentRulesOnTestFiles = apply;
            }
        });
        optionPanel.add(container, BorderLayout.NORTH);
        optionPanel.revalidate();
        optionPanel.repaint();
    }
    
    private void initRulesPanel(SonarLintEngine sonarLintEngine) {
        optionPanel.removeAll();
        JPanel languageKeyContainer = new JPanel(new FlowLayout());
        JTextField rulesFilter = new JTextField();
        rulesFilter.setColumns(20);
        JComboBox<String> comboLanguageKey = new JComboBox<>();
        sonarLintEngine.getAllRuleDetails().stream()
            .map(r -> r.getLanguage().getLanguageKey())
            .distinct()
            .forEach(comboLanguageKey::addItem);
        rulesFilter.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                rulesDefaultTableModel.setRules(sonarLintEngine, (String)comboLanguageKey.getSelectedItem(), rulesFilter.getText());
            }
        });
        comboLanguageKey.addActionListener(
            e ->
            rulesDefaultTableModel.setRules(sonarLintEngine, (String)comboLanguageKey.getSelectedItem(), rulesFilter.getText())
        );
        languageKeyContainer.add(new JLabel("language key: "));
        languageKeyContainer.add(comboLanguageKey);
        languageKeyContainer.add(new JSeparator());
        languageKeyContainer.add(new JLabel("filter: "));
        languageKeyContainer.add(rulesFilter);
        
        JPanel northContainer = new JPanel();
        northContainer.setLayout(new BoxLayout(northContainer, BoxLayout.Y_AXIS));
        JTable rulesTable = new JTable(rulesDefaultTableModel);
        rulesTable.setRowHeight(rulesTable.getRowHeight() + 2);
        rulesTable.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rulesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (rulesTable.columnAtPoint(e.getPoint()) == SonarLintRuleTableModel.SETTINGS_COLUMN_INDEX) {
                    String ruleKey = (String)rulesTable.getValueAt(rulesTable.getSelectedRow(), SonarLintRuleTableModel.KEY_COLUMN_INDEX);
                    Optional<StandaloneRuleDetails> ruleDetails = sonarLintEngine.getRuleDetails(ruleKey);
                    ruleDetails.ifPresent(standaloneRule -> {
                        if (!standaloneRule.paramDetails().isEmpty()) {
                            SonarLintOptions sonarlintOptions = Lookup.getDefault().lookup(SonarLintOptions.class);
                            SonarLintRuleSettings sonarLintRuleParameters = new SonarLintRuleSettings(sonarlintOptions, sonarLintEngine, ruleKey);
                            sonarLintRuleParameters.setVisible(true);
                        }
                    });
                }
            }
        });
        TableColumnModel columnModel = rulesTable.getColumnModel();
        columnModel.getColumn(SonarLintRuleTableModel.ENABLE_COLUMN_INDEX).setWidth(50);
        columnModel.getColumn(SonarLintRuleTableModel.SETTINGS_COLUMN_INDEX).setWidth(50);
        columnModel.getColumn(SonarLintRuleTableModel.SETTINGS_COLUMN_INDEX).setCellRenderer(new SonarLintSettingsTableCellRenderer());
        columnModel.getColumn(SonarLintRuleTableModel.SEVERITY_COLUMN_INDEX).setWidth(100);
        columnModel.getColumn(SonarLintRuleTableModel.SEVERITY_COLUMN_INDEX).setCellRenderer(new SonarLintSeverityTableCellRenderer());
        columnModel.getColumn(SonarLintRuleTableModel.KEY_COLUMN_INDEX).setWidth(100);
        columnModel.getColumn(SonarLintRuleTableModel.KEY_COLUMN_INDEX).setCellRenderer(new SonarLintRuleKeyTableCellRenderer(sonarLintEngine));
        columnModel.getColumn(SonarLintRuleTableModel.SEVERITY_COLUMN_INDEX).setCellRenderer(new SonarLintSeverityTableCellRenderer());

        rulesDefaultTableModel.setRules(sonarLintEngine, (String)comboLanguageKey.getSelectedItem(), rulesFilter.getText());
        northContainer.add(languageKeyContainer);
        northContainer.add(rulesTable.getTableHeader());
        optionPanel.add(northContainer, BorderLayout.NORTH);
        optionPanel.add(rulesTable, BorderLayout.CENTER);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        categoriesPanel = new javax.swing.JPanel();
        categoriesLabel = new javax.swing.JLabel();
        categoriesScrollPanel = new javax.swing.JScrollPane();
        categoriesList = new javax.swing.JList<>();
        optionScrollPane = new javax.swing.JScrollPane();
        optionPanel = new javax.swing.JPanel();

        setLayout(new java.awt.BorderLayout(10, 0));

        categoriesPanel.setLayout(new javax.swing.BoxLayout(categoriesPanel, javax.swing.BoxLayout.PAGE_AXIS));

        org.openide.awt.Mnemonics.setLocalizedText(categoriesLabel, org.openide.util.NbBundle.getMessage(SonarLintPanel.class, "SonarLintPanel.categoriesLabel.text")); // NOI18N
        categoriesPanel.add(categoriesLabel);

        categoriesList.setModel(new javax.swing.AbstractListModel<String>() {
            String[] strings = { "Options", "Rules", "Analyzers" };
            public int getSize() { return strings.length; }
            public String getElementAt(int i) { return strings[i]; }
        });
        categoriesList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        categoriesScrollPanel.setViewportView(categoriesList);

        categoriesPanel.add(categoriesScrollPanel);

        add(categoriesPanel, java.awt.BorderLayout.WEST);

        optionScrollPane.setViewportView(null);

        optionPanel.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        optionPanel.setLayout(new java.awt.BorderLayout());
        optionScrollPane.setViewportView(optionPanel);

        add(optionScrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    void load() {
        // TODO read settings and initialize GUI
        // Example:
        // someCheckBox.setSelected(Preferences.userNodeForPackage(SonarLintPanel.class).getBoolean("someFlag", false));
        // or for org.openide.util with API spec. version >= 7.4:
        // someCheckBox.setSelected(NbPreferences.forModule(SonarLintPanel.class).getBoolean("someFlag", false));
        // or:
        // someTextField.setText(SomeSystemOption.getDefault().getSomeStringProperty());
    }

    void store() {
        SonarLintOptions sonarLintOptions = Lookup.getDefault().lookup(SonarLintOptions.class);
        SonarLintEngine sonarLintEngine = Lookup.getDefault().lookup(SonarLintEngine.class);
        List<RuleKey> ruleKeysEnable = new ArrayList<>();
        List<RuleKey> ruleKeysDisable = new ArrayList<>();
        ruleKeyChanged.forEach((ruleKey, enable) -> {
            if (enable) {
                ruleKeysEnable.add(ruleKey);
            } else {
                ruleKeysDisable.add(ruleKey);
            }
        });
        if (sonarLintOptions != null && applyDifferentRulesOnTestFiles != null) {
            sonarLintOptions.useDifferentRulesOnTestFiles(applyDifferentRulesOnTestFiles);
        }
        sonarLintEngine.excludeRuleKeys(ruleKeysDisable);
        sonarLintEngine.includeRuleKeys(ruleKeysEnable);
        if (nodeJSPathToSave != null && nodeJSVersionToSave != null) {
            sonarLintEngine.setNodeJSPathAndVersion(nodeJSPathToSave, nodeJSVersionToSave);
        }
    }

    boolean valid() {
        // TODO check whether form is consistent and complete
        return true;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    javax.swing.JLabel categoriesLabel;
    javax.swing.JList<String> categoriesList;
    javax.swing.JPanel categoriesPanel;
    javax.swing.JScrollPane categoriesScrollPanel;
    javax.swing.JPanel optionPanel;
    javax.swing.JScrollPane optionScrollPane;
    // End of variables declaration//GEN-END:variables
}
