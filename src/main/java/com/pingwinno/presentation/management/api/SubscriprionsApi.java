package com.pingwinno.presentation.management.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pingwinno.application.SubscriptionRequestTimer;
import com.pingwinno.application.twitch.playlist.handler.UserIdGetter;
import com.pingwinno.infrastructure.HashHandler;
import com.pingwinno.infrastructure.SettingsProperties;
import com.pingwinno.infrastructure.models.SubscriptionQueryModel;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Path("/subscriptions")
public class SubscriprionsApi {
    private org.slf4j.Logger log = LoggerFactory.getLogger(getClass().getName());

    @GET
    @Path("")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTimers() {
        Map<String, Long> timers = new HashMap<>();
        for (Map.Entry<String, Instant> timer : SubscriptionRequestTimer.getTimers().entrySet()) {
            timers.put(timer.getKey(), SubscriptionRequestTimer.HUB_LEASE - Duration.between(timer.getValue(), Instant.now()).getSeconds());
        }
        try {
            return Response.status(Response.Status.OK)
                    .entity(new ObjectMapper().writeValueAsString(timers)).build();
        } catch (JsonProcessingException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PUT
    @Path("/{user}")
    public Response addSubscription(@PathParam("user") String user) {
        try {
            SubscriptionQueryModel json = new SubscriptionQueryModel("subscribe",
                    "https://api.twitch.tv/helix/streams?user_id=" +
                            UserIdGetter.getUserId(user),
                    SettingsProperties.getCallbackAddress() + ":" + SettingsProperties.getTwitchServerPort() +
                            "/handler/" + user, SubscriptionRequestTimer.HUB_LEASE, HashHandler.getKey());
            log.trace("SubscriptionQueryModel: {}", json.toString());
            SubscriptionRequestTimer subscriptionQuery = new SubscriptionRequestTimer("https://api.twitch.tv/helix/webhooks/hub", json);
            subscriptionQuery.sendSubscriptionRequest(user);
            log.debug("Sending subscription query");
            SettingsProperties.addUser(user);
            return Response.accepted().build();
        } catch (InterruptedException | IOException e) {
            return Response.notModified().build();
        }

    }

    @DELETE
    @Path("/{user}")
    public Response removeSubscription(@PathParam("user") String user) {
        SubscriptionRequestTimer.stop(user);
        SettingsProperties.removeUser(user);
        return Response.accepted().build();
    }
}
