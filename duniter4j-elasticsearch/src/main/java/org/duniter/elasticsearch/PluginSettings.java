package org.duniter.elasticsearch;

/*
 * #%L
 * UCoin Java Client :: Core API
 * %%
 * Copyright (C) 2014 - 2015 EIS
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.io.FileUtils;
import org.duniter.core.client.config.Configuration;
import org.duniter.core.client.config.ConfigurationOption;
import org.duniter.core.client.config.ConfigurationProvider;
import org.duniter.core.client.model.local.Peer;
import org.duniter.core.exception.TechnicalException;
import org.duniter.core.util.StringUtils;
import org.duniter.elasticsearch.service.ServiceLocator;
import org.elasticsearch.common.component.*;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.nuiton.config.ApplicationConfig;
import org.nuiton.config.ApplicationConfigHelper;
import org.nuiton.config.ApplicationConfigProvider;
import org.nuiton.config.ArgumentsParserException;
import org.nuiton.i18n.I18n;
import org.nuiton.i18n.init.DefaultI18nInitializer;
import org.nuiton.i18n.init.UserI18nInitializer;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import static org.nuiton.i18n.I18n.t;

/**
 * Access to configuration options
 * @author Benoit Lavenier <benoit.lavenier@e-is.pro>
 * @since 1.0
 */
public class PluginSettings extends AbstractLifecycleComponent<PluginSettings> {

    private Settings settings;

    /**
     * Delegate application config.
     */
    protected final ApplicationConfig applicationConfig;
    protected final org.duniter.core.client.config.Configuration clientConfig;

    @Inject
    public PluginSettings(org.elasticsearch.common.settings.Settings settings) {
        super(settings);

        this.settings = settings;
        this.applicationConfig = new ApplicationConfig();

        // Cascade the application config to the client module
        clientConfig = new org.duniter.core.client.config.Configuration(applicationConfig);
        Configuration.setInstance(clientConfig);

    }

    @Override
    protected void doStart() {


        // get all config providers
        Set<ApplicationConfigProvider> providers =
                ImmutableSet.of(new ConfigurationProvider());

        // load all default options
        ApplicationConfigHelper.loadAllDefaultOption(applicationConfig,
                providers);

        // Ovverides defaults
        String baseDir = settings.get("path.home");
        applicationConfig.setDefaultOption(ConfigurationOption.BASEDIR.getKey(), baseDir);
        applicationConfig.setDefaultOption(ConfigurationOption.NODE_HOST.getKey(), getNodeBmaHost());
        applicationConfig.setDefaultOption(ConfigurationOption.NODE_PORT.getKey(), String.valueOf(getNodeBmaPort()));
        applicationConfig.setDefaultOption(ConfigurationOption.NODE_PROTOCOL.getKey(), getNodeBmaPort() == 443 ? "https" : "http");

        try {
            applicationConfig.parse(new String[]{});

        } catch (ArgumentsParserException e) {
            throw new TechnicalException(t("duniter4j.config.parse.error"), e);
        }

        File appBasedir = applicationConfig.getOptionAsFile(
                ConfigurationOption.BASEDIR.getKey());

        if (appBasedir == null) {
            appBasedir = new File("");
        }
        if (!appBasedir.isAbsolute()) {
            appBasedir = new File(appBasedir.getAbsolutePath());
        }
        if (appBasedir.getName().equals("..")) {
            appBasedir = appBasedir.getParentFile().getParentFile();
        }
        if (appBasedir.getName().equals(".")) {
            appBasedir = appBasedir.getParentFile();
        }
        applicationConfig.setOption(
                ConfigurationOption.BASEDIR.getKey(),
                appBasedir.getAbsolutePath());

        // Init i18n
        try {
            initI18n();
        }
        catch(IOException e) {
            logger.error(String.format("Could not init i18n: %s", e.getMessage()), e);
        }
    }

    @Override
    protected void doStop() {

    }

    @Override
    protected void doClose() {

    }

    public String getNodeBmaHost() {
        return settings.get("duniter.host", "cgeek.fr");
    }

    public int getNodeBmaPort() {
        return settings.getAsInt("duniter.port", 9330);
    }

    public boolean isIndexBulkEnable() {
        return settings.getAsBoolean("duniter.bulk.enable", true);
    }

    public int getIndexBulkSize() {
        return settings.getAsInt("duniter.bulk.size", 1000);
    }

    public String getDefaultStringAnalyzer() {
        return settings.get("duniter.string.analyzer", "english");
    }

    public boolean reloadIndices() {
        return settings.getAsBoolean("duniter.indices.reload", false);
    }

    public boolean enableBlockchainSync()  {
        return settings.getAsBoolean("duniter.blockchain.sync.enable", false);
    }

    public File getTempDirectory() {
        return Configuration.instance().getTempDirectory();
    }

    public boolean isDevMode() {
        return settings.getAsBoolean("duniter.dev.enable", false);
    }

    public Peer checkAndGetPeer() {
        if (StringUtils.isBlank(getNodeBmaHost())) {
            logger.error("ERROR: node host is required");
            System.exit(-1);
            return null;
        }
        if (getNodeBmaPort() <= 0) {
            logger.error("ERROR: node port is required");
            System.exit(-1);
            return null;
        }

        Peer peer = new Peer(getNodeBmaHost(), getNodeBmaPort());
        return peer;
    }

    /* protected methods */

    protected void initI18n() throws IOException {

        // --------------------------------------------------------------------//
        // init i18n
        // --------------------------------------------------------------------//

        File i18nDirectory = clientConfig.getI18nDirectory();
        if (i18nDirectory.exists()) {
            // clean i18n cache
            FileUtils.cleanDirectory(i18nDirectory);
        }

        FileUtils.forceMkdir(i18nDirectory);

        if (logger.isDebugEnabled()) {
            logger.debug("I18N directory: " + i18nDirectory);
        }

        Locale i18nLocale = clientConfig.getI18nLocale();

        if (logger.isInfoEnabled()) {
            logger.info(String.format("Starts i18n with locale [%s] at [%s]",
                    i18nLocale, i18nDirectory));
        }
        I18n.init(new UserI18nInitializer(
                        i18nDirectory, new DefaultI18nInitializer(getI18nBundleName())),
                i18nLocale);
    }

    protected String getI18nBundleName() {
        return "duniter4j-elasticsearch-i18n";
    }
}
