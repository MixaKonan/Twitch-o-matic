package com.pingwinno.domain;

import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.pingwinno.infrastructure.SettingsProperties;
import com.pingwinno.infrastructure.StreamDocumentCodec;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.StringCodec;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;

public class MongoDBHandler {
    private static MongoDatabase database;
    private static MongoClient mongoClient;

    public static void connect() {
        CodecRegistry registry;
        registry = CodecRegistries.fromCodecs(new StreamDocumentCodec(), new DocumentCodec(), new StringCodec());
        mongoClient = new MongoClient(
                new ServerAddress(SettingsProperties.getMongoDBAddress(), 27017));
        String dbName = "streams";
        if (!SettingsProperties.getMongoDBName().trim().isEmpty()) {
            dbName = SettingsProperties.getMongoDBName().trim();
        }
        database = mongoClient.getDatabase(dbName).withCodecRegistry(registry);
    }

    public static void disconnect() {
        mongoClient.close();
    }


    public static MongoCollection getCollection(String user, Class aClass) {

        return database.getCollection(user, aClass);
    }

}
