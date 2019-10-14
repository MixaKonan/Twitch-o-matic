package net.streamarchive.domain;

import net.streamarchive.application.AnimatedPreviewGenerator;
import net.streamarchive.application.CommandLineExecutor;
import net.streamarchive.application.TimelinePreviewGenerator;
import net.streamarchive.application.twitch.handler.*;
import net.streamarchive.infrastructure.*;
import net.streamarchive.infrastructure.enums.State;
import net.streamarchive.infrastructure.exceptions.StreamNotFoundException;
import net.streamarchive.infrastructure.handlers.db.ArchiveDBHandler;
import net.streamarchive.infrastructure.models.Stream;
import net.streamarchive.infrastructure.models.StreamDataModel;
import net.streamarchive.infrastructure.models.StreamerNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Scope("prototype")
public class VodRecorder implements RecordThread {

    private final MasterPlaylistDownloader masterPlaylistDownloader;
    private final
    RecordStatusList recordStatusList;
    private final
    RecordThreadSupervisor recordThreadSupervisor;
    private final
    SettingsProperties settingsProperties;
    private final
    VodMetadataHelper vodMetadataHelper;
    private final
    AnimatedPreviewGenerator animatedPreviewGenerator;
    private final
    TimelinePreviewGenerator timelinePreviewGenerator;
    private final
    CommandLineExecutor commandLineExecutor;
    private final
    DashProcessing dashProcessing;
    private final
    ArchiveDBHandler archiveDBHandler;

    @Value("${net.streamarchive.dashprocessing.enabled}")
    private boolean enabled;

    private org.slf4j.Logger log;
    private MediaPlaylistDownloader mediaPlaylistDownloader = new MediaPlaylistDownloader();
    private String streamFolderPath;
    private int vodId;
    private StreamDataModel streamDataModel;
    private int threadsNumber = 1;
    private volatile LinkedHashMap<String, Double> mainPlaylist;
    private UUID uuid;
    private ExecutorService executorService;
    private Thread thisTread = Thread.currentThread();
    private boolean isRecordTerminated;
    private Stream stream = new Stream();


    public VodRecorder(RecordStatusList recordStatusList, MasterPlaylistDownloader masterPlaylistDownloader, RecordThreadSupervisor recordThreadSupervisor, SettingsProperties settingsProperties, VodMetadataHelper vodMetadataHelper, AnimatedPreviewGenerator animatedPreviewGenerator, TimelinePreviewGenerator timelinePreviewGenerator, CommandLineExecutor commandLineExecutor, DashProcessing dashProcessing, ArchiveDBHandler archiveDBHandler) {
        this.recordStatusList = recordStatusList;
        this.masterPlaylistDownloader = masterPlaylistDownloader;
        this.recordThreadSupervisor = recordThreadSupervisor;
        this.settingsProperties = settingsProperties;
        this.vodMetadataHelper = vodMetadataHelper;
        this.animatedPreviewGenerator = animatedPreviewGenerator;
        this.timelinePreviewGenerator = timelinePreviewGenerator;
        this.commandLineExecutor = commandLineExecutor;
        this.dashProcessing = dashProcessing;
        this.archiveDBHandler = archiveDBHandler;
    }

    @Override
    public void start(StreamDataModel streamDataModel) {
        this.streamDataModel = streamDataModel;

        stream.setUuid(streamDataModel.getUuid());
        stream.setTitle(streamDataModel.getTitle());
        stream.setDate(streamDataModel.getDate());
        stream.setGame(streamDataModel.getGame());
        stream.setStreamer(streamDataModel.getStreamerName());
        streamFolderPath = settingsProperties.getRecordedStreamPath() + streamDataModel.getStreamerName() + "/" + stream.getUuid().toString();
        try {
            Files.createDirectories(Paths.get(streamFolderPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
        log = new InternalLogger(getClass(), streamFolderPath);

        log.debug("Starting {} {} {}", streamDataModel.getStreamerName(), streamDataModel.getVodId(), streamDataModel.getUuid());

        uuid = streamDataModel.getUuid();

        vodId = streamDataModel.getVodId();
        recordThreadSupervisor.add(uuid, this);
        try {
            if (vodMetadataHelper.isRecording(vodId)) {
                threadsNumber = 2;
                log.info("Wait for creating vod...");
            } else {
                threadsNumber = 10;
            }
        } catch (InterruptedException e) {
            log.error("Can't start record. ", e);
            recordStatusList.changeState(uuid, State.ERROR);
        }
        try {
            recordStatusList.changeState(uuid, State.RUNNING);

            List<String> qualities = new ArrayList<>();
            if (settingsProperties.isUserExist(streamDataModel.getStreamerName())) {
                qualities = settingsProperties.getUser(streamDataModel.getStreamerName()).getQualities();
            } else {
                qualities.add("chunked");
            }
            try {

                for (String quality : qualities) {
                    Path streamPath = Paths.get(streamFolderPath + "/" + quality);
                    if (!Files.exists(streamPath)) {
                        Files.createDirectories(streamPath);
                    } else {
                        log.warn("Stream folder exist. Maybe it's unfinished task. " +
                                "If task can't be complete, it will be remove from task list.");
                        log.info("Trying finish download...");
                    }
                }

                Path streamPath = Paths.get(streamFolderPath + "/chunked");
                Files.createDirectories(streamPath);

            } catch (IOException e) {
                recordStatusList.changeState(uuid, State.ERROR);
                log.error("Can't create file or folder for VoD downloader. ", e);
            }

            archiveDBHandler.addStream(stream);
            vodId = streamDataModel.getVodId();


            executorService = Executors.newFixedThreadPool(qualities.size());
            for (String quality : qualities) {
                StreamThread streamThread = new StreamThread();
                Runnable runnable = () -> {
                    try {
                        synchronized (this) {
                            mainPlaylist = streamThread.start(quality);
                        }
                    } catch (IOException | URISyntaxException | StreamNotFoundException e) {
                        log.error("Vod downloader initialization failed. ", e);
                        recordStatusList.changeState(uuid, State.ERROR);
                        stop();
                    } catch (InterruptedException e) {
                        log.error("Vod downloader manually stopped. ", e);
                        recordStatusList.changeState(uuid, State.STOPPED);
                        stop();
                    }
                };
                executorService.execute(runnable);
            }

            executorService.shutdown();
            executorService.awaitTermination(100, TimeUnit.HOURS);
            if (mainPlaylist != null) {
                log.debug("Download preview");
                try {
                    downloadPreview(vodMetadataHelper.getVodMetadata(streamDataModel.getVodId()).getBaseUrl());

                } catch (StreamNotFoundException e) {
                    int streamLength = mainPlaylist.size() / 2;

                    commandLineExecutor.execute("ffmpeg", "-i", streamFolderPath + "/" + streamLength + ".ts", "-s",
                            "640x360", "-vframes", "1", streamFolderPath + "/" + settingsProperties.getUser(streamDataModel.getStreamerName()).getQualities()
                                    .get(settingsProperties.getUser(streamDataModel.getStreamerName()).getQualities().size() - 1) + "/preview.jpg", "-y");
                }

                animatedPreviewGenerator.generate(streamDataModel, mainPlaylist,
                        qualities.get(qualities.size() - 1));
                timelinePreviewGenerator.generate(streamDataModel, mainPlaylist,
                        qualities.get(qualities.size() - 1));

                stream.setDuration(MediaPlaylistParser.getTotalSec(mainPlaylist));

                if (enabled) {
                    if (settingsProperties.isUserExist(streamDataModel.getStreamerName())) {
                        dashProcessing.start(streamFolderPath, qualities);
                    }
                }
                archiveDBHandler.updateStream(stream);
                recordStatusList.changeState(uuid, State.COMPLETE);
                log.info("Complete");
            }
        } catch (IOException | StreamerNotFoundException | StreamNotFoundException e) {
            log.error("Vod downloader initialization failed. ", e);
            recordStatusList.changeState(uuid, State.ERROR);
            stop();
        } catch (InterruptedException e) {
            log.error("Vod downloader process failed. ", e);
            recordStatusList.changeState(uuid, State.ERROR);
            stop();
        }
    }

    @Override
    public void stop() {
        executorService.shutdownNow();
    }


    private void downloadPreview(String url) throws IOException {

        try (InputStream in = new URL(url).openStream()) {
            Files.copy(in, Paths.get(streamFolderPath + "/" + "preview.jpg"), StandardCopyOption.REPLACE_EXISTING);
            log.info("preview.jpg" + " complete");
        }
    }

    private class StreamThread {
        MediaPlaylistWriter mediaPlaylistWriter = new MediaPlaylistWriter();
        private ExecutorService executorService;
        private String quality;
        private LinkedHashMap<String, Double> mainPlaylist;

        private LinkedHashMap<String, Double> start(String quality) throws InterruptedException, IOException, URISyntaxException, StreamNotFoundException {
            this.quality = quality;
            String m3u8Link;
            m3u8Link = masterPlaylistDownloader.getPlaylist(String.valueOf(vodId), quality);
            //if stream  exist
            if (m3u8Link != null) {
                String streamPath = StreamPathExtractor.extract(m3u8Link);

                mainPlaylist = MediaPlaylistParser.getChunks(mediaPlaylistDownloader.getMediaPlaylist(m3u8Link), streamDataModel.isSkipMuted());

                executorService = Executors.newFixedThreadPool(threadsNumber);

                for (Map.Entry<String, Double> chunk : mainPlaylist.entrySet()) {
                    String chunkName = chunk.getKey();
                    Runnable runnable = () -> {
                        if (!isRecordTerminated) {
                            downloadChunk(streamPath, chunkName);
                        }
                    };
                    executorService.execute(runnable);
                }
                executorService.shutdown();
                executorService.awaitTermination(10, TimeUnit.MINUTES);
            } else {
                recordStatusList.changeState(uuid, State.ERROR);
                log.error("vod id with id {} not found. Close downloader thread...", vodId);
                stop();
            }
            this.recordCycle();
            return mainPlaylist;
        }

        private boolean refreshDownload() throws InterruptedException {
            boolean status = false;
            try {
                String m3u8Link = null;
                int counter = 0;
                //TODO Find out what the problem is
                do {
                    try {
                        m3u8Link = masterPlaylistDownloader.getPlaylist(String.valueOf(vodId), quality);
                    } catch (UnknownHostException e) {
                        counter++;
                        log.warn("UnknownHostException. cycle:{} \n Stacktrace{}", counter, e);
                    }

                } while (m3u8Link == null && counter < 10);

                if (m3u8Link != null) {
                    String streamPath = StreamPathExtractor.extract(m3u8Link);

                    LinkedHashMap<String, Double> refreshedPlaylist =
                            MediaPlaylistParser.getChunks(mediaPlaylistDownloader.getMediaPlaylist(m3u8Link), streamDataModel.isSkipMuted());

                    executorService = Executors.newFixedThreadPool(threadsNumber);

                    status = !mainPlaylist.equals(refreshedPlaylist);
                    log.trace("Renew playlist status: {}", status);
                    if (status) {
                        refreshedPlaylist.forEach((chunkName, time) -> {

                            if (!mainPlaylist.containsKey(chunkName)) {
                                log.trace("chunk: {}", chunkName);
                                Runnable runnable = () -> {
                                    if (!isRecordTerminated)
                                        downloadChunk(streamPath, chunkName);

                                };
                                executorService.execute(runnable);
                            }
                        });


                        executorService.shutdown();
                        executorService.awaitTermination(10, TimeUnit.MINUTES);
                        mainPlaylist.clear();
                        mainPlaylist.putAll(refreshedPlaylist);
                    }
                } else {
                    log.warn("UnknownHostException again. Why? I don't give a fuck.");
                }
            } catch (IOException | URISyntaxException | StreamNotFoundException e) {
                log.error("Vod downloader refresh failed. ", e);
                recordStatusList.changeState(uuid, State.ERROR);
                stop();
            }
            return status;
        }

        private void recordCycle() throws IOException, InterruptedException {

            while (vodMetadataHelper.isRecording(vodId)) {
                log.debug("Refresh download {} {} {}", streamDataModel.getStreamerName(), streamDataModel.getVodId(), streamDataModel.getUuid());
                refreshDownload();
                Thread.sleep(20 * 1000);
            }

            log.info("Finalize record...");
            int counter = 0;
            while ((!this.refreshDownload()) && (counter <= 10)) {
                log.info("Wait for renewing playlist for {} {} {}", streamDataModel.getStreamerName(), streamDataModel.getVodId(), streamDataModel.getUuid());
                Thread.sleep(10 * 1000);
                counter++;
            }
            Thread.sleep(100 * 1000);
            refreshDownload();
            log.info("End of list. Downloading last mainPlaylist");

            mediaPlaylistWriter.write(mainPlaylist, streamFolderPath + "/" + quality);
            log.debug("Download m3u8");
        }


        private void downloadChunk(String streamPath, String fileName) {
            URL website;
            URLConnection connection;

            try {
                website = new URL(streamPath + "/" + fileName);

                if (fileName.contains("muted")) {
                    fileName = fileName.replace("-muted", "");
                }

                connection = website.openConnection();

                if ((!Files.exists(Paths.get(streamFolderPath + "/" + quality + "/" + fileName))) ||
                        (connection.getContentLengthLong() > Files.size((Paths.get(streamFolderPath + "/" + quality + "/" + fileName))))) {

                    try (InputStream in = website.openStream()) {

                        Files.copy(in, Paths.get(streamFolderPath + "/" + quality + "/" + fileName), StandardCopyOption.REPLACE_EXISTING);
                        if (Integer.parseInt(fileName.replaceAll(".ts", "")) % 10 == 0) {
                            log.info(fileName + " complete");
                        }
                        log.trace(fileName + " complete");
                    }
                } else {
                    log.trace("Chunk {} exist. Skipping...", fileName);
                }
            } catch (IOException e) {
                stop();
            }
        }

        private void stop() {
            log.info("Stop record");
            log.info("Closing vod downloader...");
            if (!executorService.isShutdown()) {
                executorService.shutdownNow();
            }
            log.debug(
                    "Downloader pool stopped: {}", executorService.isShutdown());
            isRecordTerminated = true;
            thisTread.interrupt();

        }
    }


}
