package net.streamarchive.infrastructure;


import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import net.streamarchive.infrastructure.models.Settings;
import net.streamarchive.infrastructure.models.Streamer;
import net.streamarchive.repository.UserSubscriptionsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.context.restart.RestartEndpoint;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Service
@Order(1)
public class SettingsProvider {

    private static final String STREAMS_PATH = System.getProperty("user.home") + "/streams/";
    private static final String DOCKER_SETTINGS = "/etc/streamarchive/settings.json";
    private static final String STANDALONE_SETTINGS = "settings.json";
    @Autowired
    private UserSubscriptionsRepository subscriptionsRepository;
    @Autowired
    private DataValidator dataValidator;
    @Autowired
    private RestartEndpoint restartEndpoint;
    private boolean settingsIsLoaded;
    private ObjectMapper mapper = new ObjectMapper();
    private Settings settings;

    private File settingsFile;

    @PostConstruct
    private boolean init() {
        if (Files.notExists(Paths.get(DOCKER_SETTINGS))) {
            log.warn("Settings volume doesn't exist. Loading settings from working directory...");
            settingsFile = new File(STANDALONE_SETTINGS);
        } else {
            log.info("Loading settings...");
            settingsFile = new File(DOCKER_SETTINGS);
        }
        try {
            settings = mapper.readValue(settingsFile, Settings.class);
            settingsIsLoaded = true;
        } catch (IOException e) {
            log.warn("Can't load settings");
            settingsIsLoaded = false;
        }
        return settingsIsLoaded;
    }

    public boolean isInitialized() {
        return settingsIsLoaded;
    }

    public String getRemoteDBAddress() {
        return settings.getRemoteDBAddress();
    }

    public String getDbUsername() {
        return settings.getDbUsername();
    }

    public String getDbPassword() {
        return settings.getDbPassword();
    }

    public String getCallbackAddress() {
        return settings.getCallbackAddress();
    }

    public boolean isStreamerExist(String streamer) {
        return subscriptionsRepository.existsById(streamer);
    }

    public Streamer getUser(String user) {
        return subscriptionsRepository.getOne(user);
    }

    public List<Streamer> getStreamers() {
        return subscriptionsRepository.findAll();
    }

    public void addStreamer(Streamer streamer) {
        log.debug("User {} added", streamer);
        subscriptionsRepository.saveAndFlush(streamer);
    }

    public void removeUser(String user) {
        log.debug("User {} removed", user);
        subscriptionsRepository.delete(subscriptionsRepository.getOne(user));
    }

    public String getRecordedStreamPath() {
        return STREAMS_PATH;
    }

    public String getClientID() {
        return settings.getClientID();
    }

    public String getClientSecret() {
        return settings.getClientSecret();
    }

    public void saveSettings(Settings settings) throws IOException {
        dataValidator.validate(settings);
        mapper.writeValue(settingsFile, settings);
        restartEndpoint.restart();
    }

    public Settings getSettings() {
        return settings;
    }

    public String getUser() {
        return settings.getUser();
    }

    public void setUser(String user) {
        settings.setUser(user);
    }

    public String getPassword() {
        return settings.getUserPass();
    }
}


