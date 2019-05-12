package com.pingwinno.application.twitch.playlist.handler;

import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class MediaPlaylistWriter {
    private static org.slf4j.Logger log = LoggerFactory.getLogger(MediaPlaylistParser.class.getName());

    public static void write(LinkedHashMap<String, Double> playlist, String streamFolderPath) throws IOException {
        try (FileWriter fstream = new FileWriter(streamFolderPath + "/index-dvr.m3u8")) {
            BufferedWriter out = new BufferedWriter(fstream);
            log.debug("Writing playlist...");
            out.write("#EXTM3U\n" +
                    "#EXT-X-VERSION:3\n" +
                    "#EXT-X-TARGETDURATION:10\n" +
                    "#EXT-X-PLAYLIST-TYPE:EVENT\n" +
                    "#EXT-X-MEDIA-SEQUENCE:0\n");
            for (Map.Entry<String, Double> chunk : playlist.entrySet()) {

                if (chunk.getKey().contains("muted")) {
                    log.trace("muted line {}", chunk.getKey());
                    out.write("#EXTINF:" + String.format("%.3f", chunk.getValue()) + ",\n");
                    out.write(chunk.getKey().replace("-muted", ""));
                    out.newLine();
                } else {
                    log.trace("simple line {}", chunk.getKey());
                    out.write("#EXTINF:" + String.format("%.3f", chunk.getValue()) + ",\n");
                    out.write(chunk.getKey());
                    out.newLine();
                }

            }
            out.write("#EXT-X-ENDLIST");
            log.debug("done");
        }
    }
}

