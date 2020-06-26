package net.streamarchive.application;

import net.streamarchive.application.twitch.handler.VodMetadataHelper;
import net.streamarchive.infrastructure.RecordThread;
import net.streamarchive.infrastructure.enums.State;
import net.streamarchive.infrastructure.exceptions.StreamNotFoundException;
import net.streamarchive.infrastructure.models.StatusDataModel;
import net.streamarchive.infrastructure.models.StreamDataModel;
import net.streamarchive.repository.StatusRepository;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

@Service
public class RecoveryRecordHandler implements ApplicationContextAware {
    private static org.slf4j.Logger log = LoggerFactory.getLogger(RecoveryRecordHandler.class.getName());

    private final
    VodMetadataHelper vodMetadataHelper;

    private StatusRepository statusRepository;
    private ApplicationContext applicationContext;

    public RecoveryRecordHandler(VodMetadataHelper vodMetadataHelper) {

        this.vodMetadataHelper = vodMetadataHelper;
    }

    @Autowired
    public void setStatusRepository(StatusRepository statusRepository) {
        this.statusRepository = statusRepository;
    }

    @PostConstruct
    public void recoverUncompletedRecordTask() {

        log.debug("Recovering uncompleted task...");
        if (!statusRepository.findByState(State.RUNNING).isEmpty()) {
            List<StatusDataModel> dataModels = statusRepository.findByState(State.RUNNING);

            if (!dataModels.isEmpty()) {
                for (StatusDataModel dataModel : dataModels) {
                    try {
                        log.info("Found uncompleted task. {}", dataModel.getVodId());
                        StreamDataModel streamDataModel;
                        streamDataModel = vodMetadataHelper.getVodMetadata(dataModel.getVodId());
                        streamDataModel.setUuid(dataModel.getUuid());
                        streamDataModel.setStreamerName(dataModel.getUser());
                        RecordThread recordThread = applicationContext.getBean(RecordThread.class);
                        new Thread(() -> {
                            recordThread.start(streamDataModel);
                        }).start();
                    } catch (StreamNotFoundException streamNotFoundException) {
                        log.warn("Stream {} not found. Delete stream...", dataModel.getVodId());
                        statusRepository.delete(dataModel);
                    }
                }
            }
        } else log.info("Nothing to recover.");

    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
