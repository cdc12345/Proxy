package org.cdc.proxy;

import net.mcreator.plugin.JavaPlugin;
import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.events.ApplicationLoadedEvent;
import net.mcreator.plugin.events.workspace.MCreatorLoadedEvent;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.preferences.data.PreferencesData;
import net.mcreator.preferences.entries.IntegerEntry;
import net.mcreator.preferences.entries.StringEntry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdc.proxy.entries.InputEntry;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Proxy extends JavaPlugin {

    private static final String prefIdentify = "proxy";
    private Path workspace;

    private class ProxyPrefEntries{
        private static StringEntry proxyType = new StringEntry("proxyType","none","none","http","https","socks");
        private static InputEntry proxyHost = new InputEntry("proxyHost","localhost");
        private static IntegerEntry proxyPort = new IntegerEntry("proxyPort",10809);

        private static InputEntry proxyUser = new InputEntry("proxyUser","");
        private static InputEntry proxyPass = new InputEntry("proxyPass","",true);

        private static InputEntry gradleDist = new InputEntry("gradleDist","mirrors.cloud.tencent.com/gradle");
    }
    private static final Logger LOG = LogManager.getLogger("Proxy");

    public Proxy(Plugin plugin) {
        super(plugin);

        addListener(ApplicationLoadedEvent.class, event -> SwingUtilities.invokeLater(() -> {
            initPreference(PreferencesManager.PREFERENCES);
            PreferencesManager.loadPreferences(PreferencesData.CORE_PREFERENCES_KEY);
        }));
        addListener(MCreatorLoadedEvent.class , event -> SwingUtilities.invokeLater(
                ()->{
                    LOG.info("Generating gradle.prop");
                    var file = event.getMCreator().getWorkspace().getFileManager().getWorkspaceFile();
                    String type = ProxyPrefEntries.proxyType.get();
                    this.workspace = file.toPath().getParent();
                    CompletableFuture.delayedExecutor(300,TimeUnit.MILLISECONDS).execute(()->{
                        if (!type.equals("none")){
                            initWorkspaceProxyFiles(type);
                        }
                        replaceGradleDist(ProxyPrefEntries.gradleDist.get());
                    });
                }
        ));

        LOG.info("Proxy Plugin Loaded");
    }

    private void initPreference(PreferencesData data){
        data.gradle.addEntry(ProxyPrefEntries.proxyType);
        data.gradle.addEntry(ProxyPrefEntries.proxyHost);
        data.gradle.addEntry(ProxyPrefEntries.proxyPort);

        data.gradle.addEntry(ProxyPrefEntries.proxyUser);
        data.gradle.addEntry(ProxyPrefEntries.proxyPass);

        data.gradle.addEntry(ProxyPrefEntries.gradleDist);
    }

    private void initWorkspaceProxyFiles(String type){
        var prop = this.workspace.resolve("gradle.properties");
        boolean notAuth = "".equals(ProxyPrefEntries.proxyUser.get());
        String proxyHost = ProxyPrefEntries.proxyHost.get();
        int proxyPort = ProxyPrefEntries.proxyPort.get();
        String proxyUser = ProxyPrefEntries.proxyUser.get();
        String proxyPass = ProxyPrefEntries.proxyPass.get();
        {
            if (!prop.toFile().exists()) {
                String template = "";
                try {
                    template = new String(
                            this.getClass().getResourceAsStream(notAuth ? "/module/template.properties" : "module/template1.properties").readAllBytes()
                    );
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Object[] args1 = new Object[]{type, proxyHost,
                        proxyPort, proxyUser, proxyPass};
                var formatted = String.format(template, Arrays.copyOf(args1, notAuth ? 3 : 5));
                try {
                    Files.copy(new ByteArrayInputStream(formatted.getBytes()), prop);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                Properties properties = new Properties();
                try {
                    properties.load(Files.newInputStream(prop));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (checkProxy(properties)) {
                    properties.setProperty("generatedByProxy", "true");
                    var pre = "systemProp." + type;
                    properties.setProperty(pre + ".proxyHost", proxyHost);
                    properties.setProperty(pre + ".proxyPort", proxyPort + "");
                    if (!"".equals(proxyUser)) {
                        properties.setProperty(pre + ".proxyUser", proxyUser);
                        properties.setProperty(pre + ".proxyPassword", proxyPass);
                    }
                }
                try {
                    properties.store(Files.newOutputStream(prop), "Generated by Proxy Plugin");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private boolean checkProxy(Properties prop){
        if (prop.getProperty("generatedByProxy","false").equals("true")) return true;
        for (Object key:prop.keySet()){
            if (key.toString().matches("systemProp.(socks|http|https).(proxyHost|proxyPort|proxyUser|proxyPassword)")){
                return false;
            }
        }
        return true;
    }

    private void replaceGradleDist(final String gradleDist){
        var gradleWrapperProp = workspace.resolve("gradle").resolve("wrapper").resolve("gradle-wrapper.properties");
        var dist = formatDistUrl(gradleDist);
        try {
            String content = Files.readString(gradleWrapperProp);
            var form = content.replace("services.gradle.org/distributions",dist);
            Files.copy(new ByteArrayInputStream(form.getBytes(StandardCharsets.UTF_8)),gradleWrapperProp, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String formatDistUrl(final String origin){
        return origin.replaceFirst("https:?//","");
    }
}
