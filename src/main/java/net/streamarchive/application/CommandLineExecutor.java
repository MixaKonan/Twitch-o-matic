package net.streamarchive.application;

import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

@Component
public class CommandLineExecutor {

    private static org.slf4j.Logger log = LoggerFactory.getLogger(CommandLineExecutor.class.getName());
    private String path;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void execute(String... command) {

        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            if (path != null) {
                builder.directory(new File(path));
            }
            builder.redirectErrorStream(true);
            Process p = builder.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = " ";
            while (line != null) {
                line = r.readLine();
                log.info(line);
            }
        } catch (IOException e) {
            log.error("Can't run command. Exception: ", e);
        }
    }


}
