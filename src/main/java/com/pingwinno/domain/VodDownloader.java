package com.pingwinno.domain;

import com.pingwinno.application.RecordTaskHandler;
import com.pingwinno.application.twitch.playlist.handler.*;
import com.pingwinno.infrastructure.SettingsProperties;
import com.pingwinno.infrastructure.models.RecordTaskModel;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class VodDownloader {
    private static Logger log = Logger.getLogger(VodDownloader.class.getName());
    private MasterPlaylistDownloader masterPlaylistDownloader = new MasterPlaylistDownloader();
    private MediaPlaylistDownloader mediaPlaylistDownloader = new MediaPlaylistDownloader();
    private ReadableByteChannel readableByteChannel;
    private LinkedHashSet<String> chunks = new LinkedHashSet<>();
    private String streamFolderPath;
    private String vodId;
    private RecordTaskModel recordTask;
    private int threadsNumber = 1;
    public void initializeDownload(RecordTaskModel recordTask) {
        this.recordTask = recordTask;
        UUID uuid = recordTask.getUuid();
        streamFolderPath = SettingsProperties.getRecordedStreamPath() + uuid.toString();

        try {
            if (RecordStatusGetter.getRecordStatus(vodId).equals("recording")){

            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        try {
            RecordTaskHandler.saveTask(recordTask);
            try {
                Path streamPath = Paths.get(streamFolderPath);
                if (!Files.exists(streamPath)) {
                    Files.createDirectories(streamPath);
                } else {
                    log.warning("Stream folder exist. Maybe it's unfinished task. " +
                            "If task can't be complete, it will be remove from task list.");
                    log.info("Trying finish download...");
                }
            } catch (IOException e) {
                log.severe("Can't create file or folder for VoD downloader" + e.toString());
            }
            vodId = recordTask.getVodId();

            String m3u8Link = MasterPlaylistParser.parse(
                    masterPlaylistDownloader.getPlaylist(vodId));
            //if stream  exist
            if (m3u8Link != null) {
                String streamPath = StreamPathExtractor.extract(m3u8Link);
                chunks = MediaPlaylistParser.parse(mediaPlaylistDownloader.getMediaPlaylist(m3u8Link));
                ExecutorService executorService = Executors.newFixedThreadPool(20);

                for (String chunkName : chunks) {
                    Runnable runnable = () -> {
                        try {
                            downloadFile(streamPath, chunkName);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    };
                    executorService.execute(runnable);
                }
                executorService.shutdown();
                this.recordCycle();
            } else {
                log.severe("vod id with id " + vodId + " not found. Close downloader thread...");
                stopRecord();
            }

        } catch (IOException | URISyntaxException | InterruptedException e) {
            log.severe("Vod downloader initialization failed" + e);
            stopRecord();
        }
    }

    private boolean refreshDownload() throws InterruptedException {
        boolean status = false;
        try {
            String m3u8Link = MasterPlaylistParser.parse(
                    masterPlaylistDownloader.getPlaylist(vodId));
            String streamPath = StreamPathExtractor.extract(m3u8Link);
            BufferedReader reader = mediaPlaylistDownloader.getMediaPlaylist(m3u8Link);
            LinkedHashSet<String> refreshedPlaylist = MediaPlaylistParser.parse(reader);

            ExecutorService executorService = Executors.newFixedThreadPool(threadsNumber);
            for (String chunkName : refreshedPlaylist) {

                status = chunks.add(chunkName);
                System.out.println(chunkName + " " + status);
                if (status) {
                    Runnable runnable = () -> {
                        try {
                            downloadFile(streamPath, chunkName);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    };
                    executorService.execute(runnable);
                }

            }
            executorService.shutdown();
        } catch (IOException | URISyntaxException e) {
            log.severe("Vod downloader refresh failed." + e);
            stopRecord();
        }
        return status;
    }

    private void recordCycle() throws IOException, InterruptedException, URISyntaxException {
        if (!RecordStatusGetter.getRecordStatus(vodId).equals("")) {
            while (RecordStatusGetter.getRecordStatus(vodId).equals("recording")) {
                refreshDownload();
                Thread.sleep(20 * 1000);
            }
            log.fine("Finalize record...");
            while (refreshDownload()) {
                log.fine("Wait for renewing playlist");
                Thread.sleep(60 * 1000);
                log.fine("Try refresh playlist");
            }
            log.fine("End of list. Downloading last chunks");
            this.refreshDownload();
            this.downloadFile(StreamPathExtractor.extract(MasterPlaylistParser.parse(
                    masterPlaylistDownloader.getPlaylist(vodId))), "index-dvr.m3u8");

            log.info("Stop record");
            stopRecord();
        } else {
            log.severe("Getting status failed. Stop cycle...");
            stopRecord();
        }
    }

    private void downloadFile(String streamPath, String fileName) throws IOException {
        URL website = new URL(streamPath + "/" + fileName);
        URLConnection connection = website.openConnection();
        if ((!Files.exists(Paths.get(streamFolderPath + "/" + fileName))) ||
        (connection.getContentLengthLong() != Files.size((Paths.get(streamFolderPath + "/" + fileName))))){

            readableByteChannel = Channels.newChannel(website.openStream());
            FileOutputStream fos = new FileOutputStream(streamFolderPath + "/" + fileName);
            fos.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            fos.close();
            log.info(fileName + " complete");
        }else {
            log.info("Chunk exist. Skipping...");
        }
    }

    private void stopRecord() {
        try {
            log.info("Closing vod downloader...");
            readableByteChannel.close();
            masterPlaylistDownloader.close();
            mediaPlaylistDownloader.close();
            if (SettingsProperties.getExecutePostDownloadCommand()) {
                PostDownloadHandler.handleDownloadedStream();
            }
            RecordTaskHandler.removeTask(recordTask);
        } catch (IOException e) {
            log.severe("VoD downloader unexpectedly stop " + e);
        }
    }


}
