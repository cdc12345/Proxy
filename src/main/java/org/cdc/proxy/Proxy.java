package org.cdc.proxy;

import net.mcreator.plugin.JavaPlugin;
import net.mcreator.plugin.Plugin;
import net.mcreator.plugin.PluginLoader;
import net.mcreator.plugin.events.ApplicationLoadedEvent;
import net.mcreator.plugin.events.WorkspaceBuildStartedEvent;
import net.mcreator.plugin.events.workspace.MCreatorLoadedEvent;
import net.mcreator.preferences.PreferencesManager;
import net.mcreator.preferences.data.PreferencesData;
import net.mcreator.preferences.entries.IntegerEntry;
import net.mcreator.preferences.entries.StringEntry;
import net.mcreator.util.math.TimeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdc.proxy.entries.InputEntry;

import javax.swing.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class Proxy extends JavaPlugin {

    private static final String prefIdentify = "proxy";

    private class ProxyPrefEntries{
        private static StringEntry proxyType = new StringEntry("proxyType","none","none","http","https","socks");
        private static InputEntry proxyHost = new InputEntry("proxyHost","localhost");
        private static IntegerEntry proxyPort = new IntegerEntry("proxyPort",10809);

        private static InputEntry proxyUser = new InputEntry("proxyUser","");
        private static InputEntry proxyPass = new InputEntry("proxyPass","",true);
    }
    private static final Logger LOG = LogManager.getLogger("Demo Java Plugin");

    public Proxy(Plugin plugin) {
        super(plugin);

        addListener(ApplicationLoadedEvent.class, event -> SwingUtilities.invokeLater(() -> {
            initPreference(PreferencesManager.PREFERENCES);
        }));
        addListener(MCreatorLoadedEvent.class , event -> SwingUtilities.invokeLater(
                ()->{
                    LOG.info("Generating gradle.prop");
                    var file = event.getMCreator().getWorkspace().getFileManager().getWorkspaceFile();
                    initWorkspaceProxyFiles(file);
                }
        ));

        LOG.info("Proxy Plugin Loaded");
    }

    private void initPreference(PreferencesData data){

        data.gradle.addPluginEntry(prefIdentify, ProxyPrefEntries.proxyType);
        data.gradle.addPluginEntry(prefIdentify,ProxyPrefEntries.proxyHost);
        data.gradle.addPluginEntry(prefIdentify,ProxyPrefEntries.proxyPort);

        data.gradle.addPluginEntry(prefIdentify,ProxyPrefEntries.proxyUser);
        data.gradle.addPluginEntry(prefIdentify,ProxyPrefEntries.proxyPass);
    }

    private void initWorkspaceProxyFiles(File workspace){
        var prop = workspace.toPath().getParent().resolve("gradle.properties");
        LOG.info(prop.toString());
        boolean notAuth = "".equals(ProxyPrefEntries.proxyUser.get());
        String type = ProxyPrefEntries.proxyType.get();
        String proxyHost = ProxyPrefEntries.proxyHost.get();
        int proxyPort = ProxyPrefEntries.proxyPort.get();
        String proxyUser = ProxyPrefEntries.proxyUser.get();
        String proxyPass = ProxyPrefEntries.proxyPass.get();
        if (type.equals("none")){
            return;
        }
        CompletableFuture.delayedExecutor(3,TimeUnit.SECONDS).execute(()->{
            if (!prop.toFile().exists()){
                String template = "";
                try {
                    template = new String(
                            this.getClass().getResourceAsStream(notAuth?"/module/template.properties":"module/template1.properties").readAllBytes()
                    );
                } catch (IOException e) {
                    e.printStackTrace();
                }
                Object[] args1 = new Object[]{type,proxyHost,
                        proxyPort,proxyUser,proxyPass};
                var formatted = String.format(template,Arrays.copyOf(args1,notAuth?3:5));
                try {
                    Files.copy(new ByteArrayInputStream(formatted.getBytes()),prop);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                Properties properties = new Properties();
                try {
                    properties.load(Files.newBufferedReader(prop));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                if (checkProxy(properties) ) {
                    properties.setProperty("generatedByProxy","true");
                    var pre = "systemProp." + type ;
                    properties.setProperty(pre+".proxyHost", proxyHost);
                    properties.setProperty(pre+".proxyPort",proxyPort+"");
                    if (!"".equals(proxyUser)){
                        properties.setProperty(pre+".proxyUser",proxyUser);
                        properties.setProperty(pre+".proxyPassword",proxyPass);
                    }
                }
                try {
                    LOG.info("Generated");
                    properties.store(Files.newOutputStream(prop),"");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
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

}
