package net.streamarchive.application;

import net.streamarchive.infrastructure.SettingsProperties;
import net.streamarchive.infrastructure.models.Preview;
import net.streamarchive.infrastructure.models.StreamDataModel;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@Scope("prototype")

public class TimelinePreviewGenerator {
    private static org.slf4j.Logger log = LoggerFactory.getLogger(TimelinePreviewGenerator.class.getName());
    @Autowired
    private SettingsProperties settingsProperties;

    public LinkedHashMap<String, Preview> generate(StreamDataModel model, LinkedHashMap<String, Double> chunksSet)
            throws IOException {
        String pathString = settingsProperties.getRecordedStreamPath() + model.getUser() + "/" + model.getUuid();
        Files.createDirectories(Paths.get(settingsProperties.getRecordedStreamPath() + model.getUser() + "/" + model.getUuid() +
                "/timeline_preview/"));
        LinkedHashMap<String, Preview> previewList = new LinkedHashMap<>();
        int chunkNum = 0;
        double frameTime = 0.0;
        for (Map.Entry<String, Double> chunk : chunksSet.entrySet()) {
            ImageIO.write(FrameGrabber.getFrame(pathString +
                            "/" + chunk.getKey().replace("-muted", ""), 256, 144),
                    "jpeg", new File(pathString + "/timeline_preview/preview" + chunkNum + ".jpg"));
            frameTime += chunk.getValue();
            previewList.put(String.valueOf((int) frameTime), new Preview("preview" + chunkNum + ".jpg"));
            chunkNum++;
        }
        return previewList;

    }
}
