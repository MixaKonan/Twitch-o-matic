package com.pingwinno.application;

import com.pingwinno.infrastructure.SettingsProperties;
import com.pingwinno.infrastructure.models.StreamExtendedDataModel;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class TimelinePreviewGenerator {
    private static org.slf4j.Logger log = LoggerFactory.getLogger(TimelinePreviewGenerator.class.getName());

    public static LinkedHashMap<Integer, String> generate(StreamExtendedDataModel model, LinkedHashMap<String, Double> chunksSet)
            throws IOException {
        String pathString = SettingsProperties.getRecordedStreamPath() + model.getUser() + "/" + model.getUuid();
        Files.createDirectories(Paths.get(SettingsProperties.getRecordedStreamPath() + model.getUser() + "/" + model.getUuid() +
                "/timeline_preview/"));
        LinkedHashMap<Integer, String> previewList = new LinkedHashMap<>();
        int chunkNum = 0;
        double frameTime = 0.0;
        for (Map.Entry<String, Double> chunk : chunksSet.entrySet()) {
            ImageIO.write(FrameGrabber.getFrame(pathString +
                            "/" + chunk.getKey().replace("-muted", ""), 256, 144),
                    "jpeg", new File(pathString + "/timeline_preview/preview" + chunkNum + ".jpg"));
            frameTime += chunk.getValue();
            previewList.put((int) frameTime, "preview" + chunkNum + ".jpg");
            chunkNum++;
        }
        return previewList;

    }
}
