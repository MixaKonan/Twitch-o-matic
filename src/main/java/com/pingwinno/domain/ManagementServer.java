package com.pingwinno.domain;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pingwinno.infrastructure.JettyInitializationListener;
import com.pingwinno.infrastructure.SettingsProperties;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;

import javax.ws.rs.core.Application;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ManagementServer {
    private static Logger log = Logger.getLogger( ManagementServer.class.getName());
    public static void start() {

        Server server = new Server(SettingsProperties.getManagementServerPort());

        ServletContextHandler ctx = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);

        ctx.setContextPath("/start");
        server.setHandler(ctx);

        final Application application = new ResourceConfig()
                .packages("org.glassfish.jersey.examples.jackson").register(JacksonFeature.class);
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ServletHolder serHol = ctx.addServlet(ServletContainer.class, "/start/*");
        serHol.setInitOrder(2);
        //Handler package
        serHol.setInitParameter("jersey.config.server.provider.packages",
                "com.pingwinno.presentation.ManagementApi");
        try {
            server.start();
            server.join();
        } catch (Exception ex) {
            log.log(Level.SEVERE, "Server running failed: " + ex.toString(), ex);

        } finally {
            server.destroy();
        }
    }
}
