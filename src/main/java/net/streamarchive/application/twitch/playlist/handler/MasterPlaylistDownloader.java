package net.streamarchive.application.twitch.playlist.handler;


import net.streamarchive.infrastructure.HttpSevice;
import net.streamarchive.infrastructure.StreamNotFoundExeption;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ThreadLocalRandom;
@Service
@Scope("prototype")
public class MasterPlaylistDownloader {

    private static org.slf4j.Logger log = LoggerFactory.getLogger(MasterPlaylistDownloader.class.getName());
    @Autowired
    private HttpSevice httpSevice;
    private String jsonString;

    public BufferedReader getPlaylist(int vodId) throws IOException, URISyntaxException, InterruptedException, StreamNotFoundExeption {
        //make token request

        if (jsonString == null) {
            getToken(vodId);
        }
        JSONObject json = new JSONObject(jsonString);
        //make playlist request with received token
        URIBuilder builder = new URIBuilder("https://usher.twitch.tv/vod/" + vodId);
        builder.setParameter("player", "twitchweb");
        builder.setParameter("nauth", json.get("token").toString());
        builder.setParameter("nauthsig", json.get("sig").toString());
        builder.setParameter("allow_audio_only", "true");
        builder.setParameter("allow_source", "true");
        builder.setParameter("type", "any");
        builder.setParameter("p", Integer.toString(ThreadLocalRandom.current().nextInt(1, 99998)));
        HttpGet httpGet = new HttpGet(builder.build());
        CloseableHttpResponse response = httpSevice.getService(httpGet, false);
        if (response.getStatusLine().getStatusCode() == 403) {
            getToken(vodId);
        }
        if (response.getStatusLine().getStatusCode() == 404) {
            throw new StreamNotFoundExeption("Stream" + vodId + "not found");
        }
        if (response.getStatusLine().getStatusCode() == 200) {

                return new BufferedReader(new InputStreamReader
                        (httpSevice.getService(httpGet, false).getEntity().getContent(), StandardCharsets.UTF_8));


        } else {
            throw new IOException();
        }

    }

    public void close() throws IOException {
        httpSevice.close();
    }

    private void getToken(int vodId) throws IOException, InterruptedException {
        HttpGet httpGet = new HttpGet("https://api.twitch.tv/api/vods/" + vodId + "/access_token");
        httpGet.addHeader("Client-ID", "s9onp1rs4s93xvfscjfdxui9pracer");
        jsonString = EntityUtils.toString(httpSevice.getService(httpGet, true).getEntity());
        log.trace("Token is: {}", jsonString);
        while (!jsonString.startsWith("{")) {
            log.error("Token request failed. {}", jsonString);
            jsonString = EntityUtils.toString(httpSevice.getService(httpGet, true).getEntity());
            Thread.sleep(5000);
        }
    }
}


