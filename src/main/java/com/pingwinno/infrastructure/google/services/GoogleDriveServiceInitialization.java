package com.pingwinno.infrastructure.google.services;

import com.pingwinno.application.StorageHelper;
import com.pingwinno.infrastructure.SettingsProperties;

import java.util.logging.Logger;

public class GoogleDriveServiceInitialization {
    private static Logger log = Logger.getLogger(GoogleDriveService.class.getName());

    public static void initialize() {
        if (SettingsProperties.getUploadToGDrive().equals("enable")) {
            StorageHelper.initialStorageCheck();
            log.info("Initialize GDrive service");
            GoogleDriveService.createDriveService();
        }
    }
}
