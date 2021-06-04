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

import com.github.philippefichet.sonarlint4netbeans.option.Rule;
import com.github.philippefichet.sonarlint4netbeans.option.SonarRulesPage;
import com.github.philippefichet.sonarlint4netbeans.tools.HttpURLConnectionUtil;
import com.google.gson.Gson;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;
import org.sonarsource.sonarlint.core.StandaloneSonarLintEngineImpl;
import org.sonarsource.sonarlint.core.client.api.common.Language;
import org.sonarsource.sonarlint.core.client.api.common.LogOutput;
import org.sonarsource.sonarlint.core.client.api.common.PluginDetails;
import org.sonarsource.sonarlint.core.client.api.common.ProgressMonitor;
import org.sonarsource.sonarlint.core.client.api.common.RuleKey;
import org.sonarsource.sonarlint.core.client.api.common.analysis.AnalysisResults;
import org.sonarsource.sonarlint.core.client.api.common.analysis.IssueListener;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneAnalysisConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneGlobalConfiguration;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleDetails;
import org.sonarsource.sonarlint.core.client.api.standalone.StandaloneRuleParam;

/**
 * Other scanner: https://docs.sonarqube.org/display/PLUG/Plugin+Library TODO:
 * RuleDetails::isActiveByDefault
 *
 * @author FICHET Philippe
 */
public final class SonarLintEngineImpl implements SonarLintEngine {

    public static final String SONAR_JAVA_PLUGIN_VERSION = "6.15.0.25849";
    public static final String SONAR_JAVASCRIPT_PLUGIN_VERSION = "7.3.0.15071";
    private static final String PREFIX_PREFERENCE_RULE_PARAMETER = "rules.parameters.";
    private static final String PREFIX_RUNTIME_PREFERENCE = "runtime.";
    private static final String RUNTIME_NODE_JS_PATH_PREFERENCE = "nodejs.path";
    private static final String RUNTIME_NODE_JS_VERSION_PREFERENCE = "nodejs.version";
    private static final String RUNTIME_SONARQUBE_SERVER = "sonarqube.server";
    private static final String RUNTIME_QUALITY_PROFILE = "quality.profile";
    private static final String RUNTIME_PROFILE_ID = "profile.id";
    private static final String SONAR_QUBE_PRIFIX = "squid:";
    private static final String SONAR_QUBE_RULES = "sonarQubeRules";
    private static final String DEFAULT_EXCLUED_RULES = "excludedRules";
    private final Gson gson = new Gson();
    private StandaloneSonarLintEngineImpl standaloneSonarLintEngineImpl;
    private final List<Consumer<SonarLintEngine>> consumerWaitingInitialization = new ArrayList<>();
    private final List<Consumer<SonarLintEngine>> configurationChanged = new ArrayList<>();
    private final Map<String, URL> pluginURLs = new HashMap<>();
    //private Map<String, Rule> sonarQubeMap = new HashMap<>();  
    private List<RuleKey> excludedKeys = new ArrayList<>();
    private List<RuleKey> includedKeys = new ArrayList<>();
    private Set<String> keySet = new HashSet<>();

    public SonarLintEngineImpl() throws MalformedURLException {
        pluginURLs.put("java", getClass().getResource("/com/github/philippefichet/sonarlint4netbeans/resources/sonar-java-plugin-"
                + SONAR_JAVA_PLUGIN_VERSION + ".jar"));
        createInternalEngine();
        List<Map<String, String>> includedList = gson.fromJson(getPreferences().get(SONAR_QUBE_RULES, null), List.class);
        List<Map<String, String>> excludedList = gson.fromJson(getPreferences().get(DEFAULT_EXCLUED_RULES, null), List.class);

        if (includedList == null || excludedList == null) {
            getSonarQubeServer().ifPresent((String server) -> {
                getProfileId().ifPresent((String profileId) -> {
                    Map<String, String> result = HttpURLConnectionUtil.doGet(server
                            + HttpURLConnectionUtil.SONAR_RULES_SEARCH_URL.replace("{profileId}", profileId));
                    if (Boolean.valueOf(result.get(HttpURLConnectionUtil.HTTP_SUCCESS)) && !result.get(HttpURLConnectionUtil.HTTP_SUCCESS).isEmpty()) {
                        String rulesString = result.get(HttpURLConnectionUtil.HTTP_DATA);
                        SonarRulesPage rulesPage = gson.fromJson(rulesString, SonarRulesPage.class);
                        rulesPage.getRules().forEach(rule -> {
                            String key = rule.getKey();
                            if (key.contains(":")) {
                                RuleKey ruleKey = new RuleKey(rule.getLang(), key.substring(key.indexOf(":") + 1));
                                keySet.add(ruleKey.toString());
                                includedKeys.add(ruleKey);
                            }
                        });
                    }
                });
            });
            if (includedKeys.isEmpty()) {
                whenInitialized(engine -> {
                    Collection<StandaloneRuleDetails> allRuleDetails = engine.getAllRuleDetails();
                    for (StandaloneRuleDetails allRuleDetail : allRuleDetails) {
                        if (allRuleDetail.isActiveByDefault()) {
                            includedKeys.add(RuleKey.parse(allRuleDetail.getKey()));
                            keySet.add(allRuleDetail.getKey());
                        } else {
                            excludedKeys.add(RuleKey.parse(allRuleDetail.getKey()));
                        }
                    }
                });
            } 
            
            getPreferences().put(SONAR_QUBE_RULES, gson.toJson(includedKeys));
            getPreferences().put(DEFAULT_EXCLUED_RULES, gson.toJson(excludedKeys));
            
        } else {
            for (Map<String, String> inMap : includedList) {
                RuleKey ruleKey = RuleKey.parse(inMap.get("repository") + ":" + inMap.get("rule"));
                includedKeys.add(ruleKey);
                keySet.add(ruleKey.toString());
            }
            for (Map<String, String> exMap : excludedList) {
                excludedKeys.add(RuleKey.parse(exMap.get("repository") + ":" + exMap.get("rule")));
            }
        }
        
        
    }

    private void createInternalEngine() {
        standaloneSonarLintEngineImpl = null;
        new Thread(() -> {
            StandaloneGlobalConfiguration.Builder configBuilder = StandaloneGlobalConfiguration.builder()
                    .addEnabledLanguages(Language.values())
                    .addPlugins(pluginURLs.values().toArray(new URL[pluginURLs.values().size()]));
            standaloneSonarLintEngineImpl = new StandaloneSonarLintEngineImpl(configBuilder.build());
            consumerWaitingInitialization.forEach(consumer -> consumer.accept(this));
            consumerWaitingInitialization.clear();
        }).start();
    }

    @Override
    public AnalysisResults analyze(StandaloneAnalysisConfiguration configuration, IssueListener issueListener, LogOutput logOutput, ProgressMonitor monitor) {
        waitingInitialization();
        return standaloneSonarLintEngineImpl.analyze(configuration, issueListener, logOutput, monitor);
    }

    @Override
    public void waitingInitialization() {
        while (standaloneSonarLintEngineImpl == null) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ex) {
                Logger.getLogger(SonarLintEngineImpl.class.getName()).log(Level.SEVERE, null, ex);
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void whenInitialized(Consumer<SonarLintEngine> consumer) {
        if (standaloneSonarLintEngineImpl != null) {
            consumer.accept(this);
        } else {
            consumerWaitingInitialization.add(consumer);
        }
    }

    @Override
    public Collection<StandaloneRuleDetails> getAllRuleDetails() {
        waitingInitialization();
        return standaloneSonarLintEngineImpl.getAllRuleDetails();
    }

    @Override
    public Collection<PluginDetails> getPluginDetails() {
        waitingInitialization();
        return standaloneSonarLintEngineImpl.getPluginDetails();
    }

    @Override
    public Optional<StandaloneRuleDetails> getRuleDetails(String ruleKey) {
        waitingInitialization();
        return standaloneSonarLintEngineImpl.getRuleDetails(ruleKey);
    }

    @Override
    public Preferences getPreferences() {
        return NbPreferences.forModule(SonarLintEngineImpl.class);
    }

    @Override
    public void whenConfigurationChanged(Consumer<SonarLintEngine> consumer) {
        configurationChanged.add(consumer);
    }

    private void fireConfigurationChange() {
        configurationChanged.forEach(consumer -> consumer.accept(this));
    }

    @Override
    public void setRuleParameter(String ruleKey, String parameterName, String parameterValue) {
        getPreferences().put(PREFIX_PREFERENCE_RULE_PARAMETER + ruleKey.replace(":", ".") + "." + parameterName, parameterValue);
        fireConfigurationChange();
    }

    @Override
    public Map<RuleKey, Map<String, String>> getRuleParameters() {
        Map<RuleKey, Map<String, String>> ruleParameters = new HashMap<>();
        for (StandaloneRuleDetails standaloneRule : getAllRuleDetails()) {
            String ruleKey = standaloneRule.getKey();
            for (StandaloneRuleParam param : standaloneRule.paramDetails()) {
                if (param instanceof StandaloneRuleParam) {
                    StandaloneRuleParam ruleParam = (StandaloneRuleParam) param;
                    String parameterValue = getPreferences().get(PREFIX_PREFERENCE_RULE_PARAMETER + ruleKey.replace(":", ".") + "." + ruleParam.name(), ruleParam.defaultValue());
                    if (parameterValue != null && !parameterValue.equals(ruleParam.defaultValue())) {
                        RuleKey key = RuleKey.parse(standaloneRule.getKey());
                        Map<String, String> params = ruleParameters.get(key);
                        if (params == null) {
                            params = new HashMap<>();
                            ruleParameters.put(key, params);
                        }
                        params.put(ruleParam.name(), parameterValue);
                    }
                }
            }
        }
        return ruleParameters;
    }

    @Override
    public void removeRuleParameter(String ruleKey, String parameterName) {
        getPreferences().remove(PREFIX_PREFERENCE_RULE_PARAMETER + ruleKey.replace(":", ".") + "." + parameterName);
        fireConfigurationChange();
    }

    @Override
    public Optional<String> getRuleParameter(String ruleKey, String parameterName) {
        return getRuleDetails(ruleKey).flatMap(ruleDetail
                -> SonarLintUtils.searchRuleParameter(ruleDetail, parameterName)
                        .flatMap(param -> {
                            String parameterValue = getPreferences().get(PREFIX_PREFERENCE_RULE_PARAMETER + ruleKey.replace(":", ".") + "." + parameterName, param.defaultValue());
                            if (parameterValue != null && !parameterValue.equals(param.defaultValue())) {
                                return Optional.of(parameterValue);
                            } else {
                                return Optional.empty();
                            }
                        })
        );
    }

    @Override
    public void setSonarQubeServer(String server, String profieName, String profileId) {
        getPreferences().put(PREFIX_RUNTIME_PREFERENCE + RUNTIME_SONARQUBE_SERVER, server);
        getPreferences().put(PREFIX_RUNTIME_PREFERENCE + RUNTIME_QUALITY_PROFILE, profieName);
        getPreferences().put(PREFIX_RUNTIME_PREFERENCE + RUNTIME_PROFILE_ID, profileId);
        createInternalEngine();
    }

    @Override
    public Optional<String> getSonarQubeServer() {
        return Optional.ofNullable(getPreferences().get(PREFIX_RUNTIME_PREFERENCE + RUNTIME_SONARQUBE_SERVER, null));
    }

    @Override
    public Optional<String> getQualityProfile() {
        return Optional.ofNullable(getPreferences().get(PREFIX_RUNTIME_PREFERENCE + RUNTIME_QUALITY_PROFILE, null));
    }

    @Override
    public Optional<String> getProfileId() {
        return Optional.ofNullable(getPreferences().get(PREFIX_RUNTIME_PREFERENCE + RUNTIME_PROFILE_ID, null));
    }

    @Override
    public void addAllSonarQubeRule(List<Rule> rules) {
        includedKeys.clear();
        keySet.clear();
        rules.forEach(rule -> {
            String key = rule.getKey();
            if (key.contains(":")) {
                RuleKey ruleKey = new RuleKey(rule.getLang(), key.substring(key.indexOf(":") + 1));
                keySet.add(ruleKey.toString());
                includedKeys.add(ruleKey);
            }
        });
        getPreferences().put(SONAR_QUBE_RULES, gson.toJson(includedKeys));

        getAllRuleDetails().forEach((ruleDetail) -> {
            if (!keySet.contains(ruleDetail.getKey())) {
                excludedKeys.add(RuleKey.parse(ruleDetail.getKey()));
            }
        });
        getPreferences().put(DEFAULT_EXCLUED_RULES, gson.toJson(excludedKeys));

        fireConfigurationChange();
    }

    @Override
    public List<RuleKey> getExcludedKeys() {
        return excludedKeys;
    }

    @Override
    public List<RuleKey> getIncludedKeys() {
        return includedKeys;
    }

    @Override
    public boolean isInCludedRule(StandaloneRuleDetails ruleDetails) {
        return keySet.contains(ruleDetails.getKey());
    }

}
