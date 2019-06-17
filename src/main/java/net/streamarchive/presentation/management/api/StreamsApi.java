package net.streamarchive.presentation.management.api;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.streamarchive.application.DateConverter;
import net.streamarchive.infrastructure.RecordThread;
import net.streamarchive.repository.StatusRepository;
import net.streamarchive.application.StorageHelper;
import net.streamarchive.application.twitch.playlist.handler.VodMetadataHelper;
import net.streamarchive.domain.MongoDBHandler;
import net.streamarchive.domain.VodRecorder;
import net.streamarchive.infrastructure.RecordStatusList;
import net.streamarchive.infrastructure.SettingsProperties;
import net.streamarchive.infrastructure.StreamNotFoundExeption;
import net.streamarchive.infrastructure.enums.StartedBy;
import net.streamarchive.infrastructure.enums.State;
import net.streamarchive.infrastructure.models.AddRequestModel;
import net.streamarchive.infrastructure.models.StatusDataModel;
import net.streamarchive.infrastructure.models.StreamDataModel;
import org.apache.commons.io.FileUtils;
import org.bson.Document;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Projections.fields;
import static com.mongodb.client.model.Projections.include;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

/**
 * API for database and streams management
 * Endpoint {@code /database}
 */
@Service
@Path("/database")
public class StreamsApi {
    @Autowired
    StatusRepository statusRepository;
    @Autowired
    RecordStatusList recordStatusList;
    @Autowired
    RecordThread vodRecorder;

    private org.slf4j.Logger log = LoggerFactory.getLogger(getClass().getName());

    /**
     * Start recording new stream
     * Endpoint {@code /database/add}
     *
     * @param requestModel request params
     * @see AddRequestModel for review json fields
     */
    @PUT
    @Path("/streams")
    @Produces(MediaType.TEXT_HTML)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response startRecord(AddRequestModel requestModel) {
        Response response;
        StreamDataModel streamMetadata = null;

        try {
            log.trace("type: {}", requestModel.getType());
            log.trace("value: {}", requestModel.getValue());
            if (requestModel.getType().equals("user")) {
                streamMetadata = VodMetadataHelper.getLastVod(requestModel.getValue());
            } else if (requestModel.getType().equals("vod")) {
                streamMetadata = VodMetadataHelper.getVodMetadata(Integer.valueOf(requestModel.getValue()));
            }
            if (streamMetadata != null) {
                //set another parent folder/db for stream ( for example if streamer is guest on another chanel)
                if (requestModel.getWriteTo() != null) {
                    streamMetadata.setUser(requestModel.getWriteTo());
                }

                if (streamMetadata.getVodId() != 0) {
                    //if uuid == null start new record, else start validation
                    if (requestModel.getUuid() != null) {
                        streamMetadata.setUuid(requestModel.getUuid());
                        recordStatusList.addStatus
                                (new StatusDataModel(streamMetadata.getVodId(), StartedBy.VALIDATION, DateConverter.convert(LocalDateTime.now()),
                                        State.INITIALIZE, streamMetadata.getUuid(), streamMetadata.getUser()));
                        //if record exist in status DB then run validation
                    } else if (statusRepository.existsById(streamMetadata.getVodId())) {
                        streamMetadata.setUuid(statusRepository.findById(streamMetadata.getVodId()).get().getUuid());
                        recordStatusList.addStatus
                                (new StatusDataModel(streamMetadata.getVodId(), StartedBy.VALIDATION, DateConverter.convert(LocalDateTime.now()),
                                        State.INITIALIZE, streamMetadata.getUuid(), streamMetadata.getUser()));
                    } else {
                        streamMetadata.setUuid(StorageHelper.getUuidName());
                        recordStatusList.addStatus
                                (new StatusDataModel(streamMetadata.getVodId(), StartedBy.MANUAL, DateConverter.convert(LocalDateTime.now()),
                                        State.INITIALIZE, streamMetadata.getUuid(), streamMetadata.getUser()));
                    }

                    streamMetadata.setSkipMuted(requestModel.isSkipMuted());
                    StreamDataModel finalStreamMetadata = streamMetadata;

                    new Thread(() -> vodRecorder.start(finalStreamMetadata)).start();

                    Date startedAt = streamMetadata.getDate();
                    log.info("Record started at:{} ", startedAt);
                    response = Response.accepted().build();
                } else {
                    log.error("Stream {} not found", requestModel.getValue());
                    response = Response.status(Response.Status.NOT_FOUND).build();
                }
            } else {
                response = Response.status(Response.Status.NOT_ACCEPTABLE).build();
            }
        } catch (IOException | InterruptedException | StreamNotFoundExeption e) {
            response = Response.status(500, e.toString()).build();
            log.error("Can't start record {}", e);
        }
        return response;
    }

    /**
     * Delete stream from database
     * Endpoint {@code /database/{user}/{uuid}/}
     *
     * @param uuid        UUID of stream
     * @param user        name of streamer
     * @param deleteMedia if "true" also delete stream from storage
     * @return return notModified if can't delete stream from storage (right issue/etc)
     */
    @Path("/streams/{user}/{uuid}")
    @DELETE
    public Response deleteStream(@PathParam("uuid") String uuid, @PathParam("user") String user, @QueryParam("deleteMedia") String deleteMedia) {

        MongoDBHandler.getCollection(user).deleteOne(new Document("_id", uuid));
        log.info("delete stream {}", uuid);
        if (deleteMedia.equals("true")) {
            try {
                FileUtils.deleteDirectory(new File(SettingsProperties.getRecordedStreamPath() + "" + user + "/" + uuid));
            } catch (IOException e) {
                log.error("can't delete media {] ", e);
                return Response.notModified().build();
            }
        }

        return Response.accepted().build();
    }

    /**
     * Update database record
     * Endpoint {@code /database/{user}/{uuid}/}
     *
     * @param user      name of streamer
     * @param uuid      UUID of stream
     * @param dataModel updated metadata
     * @return return ok
     * @see StreamDataModel for see required fields
     */
    @Path("/streams/{user}/{uuid}")
    @PATCH
    public Response updateStream(@PathParam("user") String user, @PathParam("uuid") String uuid, StreamDataModel dataModel) {

        MongoDBHandler.getCollection(user).updateOne(eq("_id", uuid),
                combine(set("date", dataModel.getDate()), set("title", dataModel.getTitle()), set("game", dataModel.getGame())));

        return Response.ok().build();

    }

    /**
     * Return streams array of selected streamer
     * Endpoint {@code /database/streams}
     *
     * @param user streamer name
     * @return array of streamer streams
     */
    @Path("/streams/{user}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStreamsList(@PathParam("user") String user) {
        log.trace(user);
        if (Arrays.deepToString(SettingsProperties.getUsers()).contains(user)) {
            try {
                return Response.status(Response.Status.OK)
                        .entity(new ObjectMapper().writeValueAsString(MongoDBHandler.getCollection(user).
                                find().projection(fields(include("title", "date", "game"))).into(new ArrayList()))
                                .replaceAll("_id", "uuid")).build();
            } catch (JsonProcessingException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return Response.status(Response.Status.NOT_FOUND).build();
        }

    }
}
