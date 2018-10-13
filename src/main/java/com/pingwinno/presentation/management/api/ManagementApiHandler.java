package com.pingwinno.presentation.management.api;


import com.pingwinno.application.StorageHelper;
import com.pingwinno.application.twitch.playlist.handler.VodMetadataHelper;
import com.pingwinno.domain.VodDownloader;
import com.pingwinno.infrastructure.models.StreamExtendedDataModel;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;

@Path("/start")
public class ManagementApiHandler {
    private org.slf4j.Logger log = LoggerFactory.getLogger(getClass().getName());


    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response startRecord(@QueryParam("type") String type, @QueryParam("value") String value) {
        Response response;
        StreamExtendedDataModel streamMetadata = null;
        try {
            log.trace("type: {}",type);
            log.trace("value: {}",value);
            if (type.equals("user")) {
                streamMetadata = VodMetadataHelper.getLastVod(value);
            } else if (type.equals("vod")) {
                streamMetadata = VodMetadataHelper.getVodMetadata(value);
            }
            if (streamMetadata != null) {
                VodDownloader vodDownloader = new VodDownloader();
                if (streamMetadata.getVodId() != null) {

                    streamMetadata.setUuid(StorageHelper.getUuidName());
                    StreamExtendedDataModel finalStreamMetadata = streamMetadata;
                    new Thread(() -> vodDownloader.initializeDownload(finalStreamMetadata)).start();

                    String startedAt = streamMetadata.getDate();
                    log.info("Record started at:{} ", startedAt);
                    response = Response.accepted().build();
                } else {
                    log.error("Stream {] not found", value);
                    response = Response.status(Response.Status.NOT_FOUND).build();
                }
            } else {
                response = Response.status(Response.Status.NOT_ACCEPTABLE).build();
            }
        } catch (IOException | InterruptedException e) {
            response = Response.status(500, e.toString()).build();
            log.error("Can't start record {}", e);
        }
        return response;
    }


}
