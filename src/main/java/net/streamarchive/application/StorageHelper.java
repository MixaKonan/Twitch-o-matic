package net.streamarchive.application;

import lombok.extern.slf4j.Slf4j;
import net.streamarchive.infrastructure.SettingsProvider;
import net.streamarchive.infrastructure.models.StorageState;
import net.streamarchive.infrastructure.models.Streamer;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@Service
public class StorageHelper {

    @Autowired
    private SettingsProvider settingsProperties;

    public Map<String, Integer> getFreeSpace() throws IOException {
        Map<String, Integer> freeSpace = new HashMap<>();
        for (Streamer streamer : settingsProperties.getStreamers()) {
            freeSpace.put(streamer.getName(), (int) (Files.getFileStore(Paths.get(settingsProperties.getRecordedStreamPath() + streamer.getName())).getUsableSpace() / 1073741824));
        }
        return freeSpace;
    }

    public List<StorageState> getStorageState() throws IOException {
        List<StorageState> storageStates = new ArrayList<>();
        for (Streamer streamer : settingsProperties.getStreamers()) {
            StorageState storageState = new StorageState();
            storageState.setUser(streamer.getName());
            storageState.setTotalStorage((int) (Files.getFileStore(Paths.get(settingsProperties.getRecordedStreamPath() + streamer.getName())).getTotalSpace() / 1073741824));
            storageState.setFreeStorage((int) (Files.getFileStore(Paths.get(settingsProperties.getRecordedStreamPath() + streamer.getName())).getUsableSpace() / 1073741824));
            storageState.setUsedStorage((FileUtils.sizeOfDirectory(new File(settingsProperties.getRecordedStreamPath() + streamer.getName())) / 1073741824.));
            storageStates.add(storageState);
        }
        return storageStates;
    }

    public boolean creatingRecordedPath(String user) throws IOException {

        if (!Files.exists(Paths.get(settingsProperties.getRecordedStreamPath() + user))) {
            return (Files.createDirectories(Paths.get(settingsProperties.getRecordedStreamPath() + user)) != null);
        }
        return true;
    }

    public boolean initialStorageCheck() throws IOException {
        boolean pass = true;
        for (Streamer streamer : settingsProperties.getStreamers()) {
            if (!Files.exists(Paths.get(settingsProperties.getRecordedStreamPath() + streamer)) && !Files.exists(Paths.get(settingsProperties.getRecordedStreamPath() + streamer))) {
                log.info("Folder not exist!");
                log.info("Try create folder...");
                if (!creatingRecordedPath(streamer.getName())) {
                    pass = false;
                } else {
                    log.info("Success!");
                }
            } else if (!Files.isWritable(Paths.get(settingsProperties.getRecordedStreamPath() + streamer.getName()))) {
                log.warn("Can't write in {}", settingsProperties.getRecordedStreamPath());
                log.warn("Check permissions or change RecordedStreamPath in config_test.prop");
                pass = false;
            }
        }
        for (Streamer streamer : settingsProperties.getStreamers()) {
            log.info("Free space for {} is: {} GB", streamer, getFreeSpace().get(streamer.getName()));
        }
        return pass;

    }

    public UUID getUuidName() {
        UUID uuid = UUID.randomUUID();
        Path streamPath = Paths.get(settingsProperties.getRecordedStreamPath() + uuid.toString());
        if (Files.exists(streamPath) && Files.isDirectory(streamPath)) {
            //generate uuid again
            uuid = getUuidName();
        }
        return uuid;
    }
}
