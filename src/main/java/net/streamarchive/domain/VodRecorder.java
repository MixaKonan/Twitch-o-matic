package net.streamarchive.domain;

import net.streamarchive.application.postprocessing.CommandLineExecutor;
import net.streamarchive.application.twitch.handler.MasterPlaylistDownloader;
import net.streamarchive.application.twitch.handler.PlaylistWriter;
import net.streamarchive.application.twitch.handler.VodMetadataHelper;
import net.streamarchive.infrastructure.*;
import net.streamarchive.infrastructure.data.handler.StorageService;
import net.streamarchive.infrastructure.enums.State;
import net.streamarchive.infrastructure.exceptions.StreamNotFoundException;
import net.streamarchive.infrastructure.handlers.db.ArchiveDBHandler;
import net.streamarchive.infrastructure.models.Stream;
import net.streamarchive.infrastructure.models.StreamDataModel;
import net.streamarchive.infrastructure.models.StreamFileModel;
import net.streamarchive.infrastructure.models.StreamerNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.streamarchive.util.UrlFormatter.format;

@Service
@Scope(scopeName = "prototype", proxyMode = ScopedProxyMode.INTERFACES)
public class VodRecorder implements RecordThread {

    @Value("${net.streamarchive.storage}")
    private String storageType;
    @Autowired
    private RecordStatusList recordStatusList;
    @Autowired
    private RecordThreadSupervisor recordThreadSupervisor;

    @Autowired
    private SettingsProvider settingsProperties;
    @Autowired
    private VodMetadataHelper vodMetadataHelper;

    private CommandLineExecutor commandLineExecutor;
    @Autowired
    private ArchiveDBHandler archiveDBHandler;

    @Autowired
    private StorageService dataHandler;

    private org.slf4j.Logger log;

    private String streamFolderPath;
    private int vodId;
    private StreamDataModel streamDataModel;
    private int threadsNumber = 1;
    private UUID uuid;
    private Thread thisTread = Thread.currentThread();
    private boolean isRecordTerminated;
    private Stream stream = new Stream();
    private StreamThread streamThread = new StreamThread();


    @Override
    public void start(StreamDataModel streamDataModel) {
        this.streamDataModel = streamDataModel;
        stream.setUuid(streamDataModel.getUuid());
        stream.setStreamer(streamDataModel.getStreamerName());
        stream.setDate(streamDataModel.getDate());
        stream.setGame(streamDataModel.getGame());
        stream.setTitle(streamDataModel.getTitle());
        streamDataModel = vodMetadataHelper.getVodMetadata(streamDataModel);
        streamFolderPath = format(settingsProperties.getRecordedStreamPath(), streamDataModel.getStreamerName(), stream.getUuid().toString());
        commandLineExecutor = CommandLineExecutor.builder().path(streamFolderPath).build();
        try {
            Files.createDirectories(Paths.get(streamFolderPath));
        } catch (IOException e) {
            e.printStackTrace();
        }
        log = new InternalLogger(getClass(), streamFolderPath);
        log.trace("Object: {}", this.hashCode());

        log.debug("Starting {} {} {}", streamDataModel.getStreamerName(), streamDataModel.getVodId(), streamDataModel.getUuid());

        uuid = streamDataModel.getUuid();

        vodId = streamDataModel.getVodId();
        recordThreadSupervisor.add(uuid, this);
        try {
            if (vodMetadataHelper.isRecording(vodId)) {
                threadsNumber = 2;
                log.info("Wait for creating vod...");
            } else {
                threadsNumber = 5;
            }
            if ("file".equals(storageType)) {
                threadsNumber *= threadsNumber;
            }
        } catch (InterruptedException e) {
            log.error("Can't start record. ", e);
            recordStatusList.changeState(uuid, State.ERROR);
        }
        try {
            recordStatusList.changeState(uuid, State.RUNNING);

            try {
                dataHandler.initStreamStorage(streamDataModel.getQualities().keySet(), streamFolderPath);
            } catch (IOException e) {
                recordStatusList.changeState(uuid, State.ERROR);
                log.error("Can't create file or folder for VoD downloader. ", e);
            }

            archiveDBHandler.addStream(stream);


            vodId = streamDataModel.getVodId();

            log.info("start stream {}", streamDataModel.getVodId());
            streamThread.start();


            stream.setDuration(streamDataModel.getDuration());
            log.trace("Stream data: {}", stream);
            archiveDBHandler.updateStream(stream);
            recordStatusList.changeState(uuid, State.COMPLETE);
            log.info("Record complete");
            log.info("Run postprocessing...");
            var postprocessingParameter = String.join("/", settingsProperties.getRecordedStreamPath(), getFirstQuality());
            log.trace("Postprocessing parameter: {}", postprocessingParameter);
            commandLineExecutor.execute(settingsProperties.getSettingsPath() + "/postprocessing.sh", postprocessingParameter);
            log.info("Postprocessing complete");
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
        streamThread.stop();
    }

    private String getFirstQuality() {
        return format(streamDataModel.getStreamerName(),
                stream.getUuid().toString(),
                streamDataModel.getQualities().keySet().stream().findFirst().get());
    }


    private class StreamThread {

        private ExecutorService executorService;
        private int previousDuration;

        private Set<StreamFileModel> previousFilesSet;
        private Map<String, Set<StreamFileModel>> playlists;

        private void start() throws InterruptedException, IOException, StreamNotFoundException {
            log.debug("stream started {}", streamDataModel.getVodId());

            previousDuration = streamDataModel.getDuration();
            executorService = Executors.newFixedThreadPool(threadsNumber);
            playlists = MasterPlaylistDownloader.getPlaylist(streamDataModel);
            log.debug("retrieving playlists {}", streamDataModel.getVodId());
            previousFilesSet = playlists
                    .values()
                    .parallelStream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());
            log.debug("playlists loaded {}", streamDataModel.getVodId());
            for (StreamFileModel file : previousFilesSet) {
                Runnable runnable = () -> {
                    if (!isRecordTerminated) {
                        try {
                            downloadChunk(file);
                        } catch (IOException e) {
                            log.error("Chunk download failed. ", e);
                            recordStatusList.changeState(uuid, State.ERROR);
                            stop();
                        }
                    }
                };
                executorService.execute(runnable);
            }
            executorService.shutdown();
            executorService.awaitTermination(1000, TimeUnit.MINUTES);
            if (vodMetadataHelper.isRecording(streamDataModel.getVodId())) {
                log.debug("Stream is online. Run record cycle...");
                this.recordCycle();
            } else {
                finalizeRecord();
            }
        }

        private boolean refreshDownload() throws InterruptedException {
            boolean isPlaylistRefreshed = false;
            streamDataModel = vodMetadataHelper.getVodMetadata(streamDataModel);

            try {
                isPlaylistRefreshed = previousDuration < streamDataModel.getDuration();
                log.trace("Renew playlist status: {}", isPlaylistRefreshed);
                if (isPlaylistRefreshed) {
                    runDownloadThreads();
                }
                previousDuration = streamDataModel.getDuration();
            } catch (IOException e) {
                log.error("Vod downloader refresh failed. ", e);
                recordStatusList.changeState(uuid, State.ERROR);
                stop();
            }
            return isPlaylistRefreshed;
        }

        private void runDownloadThreads() throws IOException, InterruptedException {
            var newPlaylists = MasterPlaylistDownloader.getPlaylist(streamDataModel);
            Set<StreamFileModel> newFilesSet = newPlaylists
                    .values()
                    .parallelStream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());

            executorService = Executors.newFixedThreadPool(threadsNumber);

            newFilesSet.forEach((file) -> {
                if (!previousFilesSet.contains(file)) {
                    log.trace("chunk: {}", file);
                    Runnable runnable = () -> {
                        if (!isRecordTerminated) {
                            try {
                                downloadChunk(file);
                            } catch (IOException e) {
                                log.error("Chunk download failed. ", e);
                                recordStatusList.changeState(uuid, State.ERROR);
                                stop();
                            }
                        }

                    };
                    executorService.execute(runnable);
                }
            });

            executorService.shutdown();
            executorService.awaitTermination(1000, TimeUnit.MINUTES);
            previousFilesSet = newFilesSet;
            playlists = newPlaylists;
        }

        private void recordCycle() throws InterruptedException {

            try {
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
            } catch (StreamNotFoundException e) {
                log.error("Stream has been deleted. Successful finalization is not guaranteed. ", e);
                for (int i = 0; i < 2; i++) {
                    Thread.sleep(120 * 1000);
                    try {
                        runDownloadThreads();
                    } catch (IOException ioException) {
                        recordStatusList.changeState(uuid, State.ERROR);
                    }
                }
            } finally {
                finalizeRecord();
            }


        }

        private void finalizeRecord() {

            log.info("End of list. Downloading last mainPlaylist");
            var streamBasePath = format(streamDataModel.getStreamerName(), streamDataModel.getUuid().toString());
            playlists.forEach((key, value) -> {
                try {
                    log.debug("Write {} playlist...", key);
                    dataHandler.write(PlaylistWriter.writeMedia(value), streamBasePath, format(key, "index-dvr.m3u8"));
                    log.debug("Write {} playlist complete", key);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            log.debug("Download m3u8");
            try {
                log.debug("Write master playlist...");
                dataHandler.write(PlaylistWriter.writeMaster(streamDataModel), streamBasePath, "master.m3u8");
                downloadPreview(vodMetadataHelper.getVodMetadata(streamDataModel.getVodId()).getPreviewUrl(), streamBasePath);

            } catch (StreamNotFoundException | IOException e) {

            }

        }


        private void downloadChunk(StreamFileModel streamFileModel) throws IOException {
            URL website;
            URLConnection connection;

            website = new URL(format(streamFileModel.getBaseUrl(), streamFileModel.getFileName()));
            String fileName;
            if (streamFileModel.getFileName().contains("muted")) {
                fileName = streamFileModel.getFileName().replace("-muted", "");
            } else {
                fileName = streamFileModel.getFileName();
            }
            connection = website.openConnection();
            if (connection.getContentLengthLong() > dataHandler.size(streamFileModel.getBasePath(), fileName)) {
                try (InputStream in = website.openStream()) {
                    dataHandler.write(in, streamFileModel.getBasePath(), fileName);
                    log.trace(fileName + " complete");
                }
            } else {
                log.trace("Chunk {} exist. Skipping...", fileName);
            }
        }

        private void downloadPreview(String url, String basePath) throws IOException {
            try (InputStream in = new URL(url).openStream()) {
                dataHandler.write(in, basePath, "/preview.jpg");
                log.info("Download main preview complete");
            }
        }


        private void stop() {
            log.info("Stop record");
            log.info("Closing vod downloader...");
            recordStatusList.changeState(uuid, State.STOPPED);
            if (!executorService.isShutdown()) {
                executorService.shutdownNow();
            }
            log.debug("Downloader pool stopped: {}", executorService.isShutdown());
            isRecordTerminated = true;
            thisTread.interrupt();
        }
    }


}
