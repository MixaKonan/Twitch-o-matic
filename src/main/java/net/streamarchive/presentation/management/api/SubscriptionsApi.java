package net.streamarchive.presentation.management.api;


import net.streamarchive.application.SubscriptionRequest;
import net.streamarchive.infrastructure.SettingsProperties;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API for chanel subscriptions management.
 * Endpoint {@code /subscriptions}
 */

@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionsApi {

    private final
    SubscriptionRequest subscriptionRequest;
    private final
    SettingsProperties settingsProperties;

    private org.slf4j.Logger log = LoggerFactory.getLogger(getClass().getName());

    public SubscriptionsApi(SubscriptionRequest subscriptionRequest, SettingsProperties settingsProperties) {
        this.subscriptionRequest = subscriptionRequest;
        this.settingsProperties = settingsProperties;
    }

    /**
     * Method returns list of current active subscriptions.
     *
     * @return list of current active subscriptions.
     */
    @RequestMapping(method = RequestMethod.GET)
    public Map<String, List<String>> getTimers() {
        return settingsProperties.getUsers();
    }

    /**
     * Method adds new chanel subscription.
     *
     * @param user name of chanel
     * @param quality list of streams quality
     */
    @RequestMapping(value = "/{user}", method = RequestMethod.PUT)
    public void addSubscription(@PathVariable("user") String user, @RequestParam("quality") List<String> quality) {
        try {
            subscriptionRequest.sendSubscriptionRequest(user);
            Map<String, List<String>> users = new HashMap<>();
            users.put(user, quality);
            users.get(user).sort(Comparator.comparing(String::length));
            settingsProperties.addUser(users);
        } catch (IOException e) {
            throw new InternalServerErrorException();
        }
    }

    /**
     * Method delete chanel subscription.
     *
     * @param user name of chanel
     */

    @RequestMapping(value = "/{user}", method = RequestMethod.DELETE)
    public void removeSubscription(@PathVariable("user") String user) {
        settingsProperties.removeUser(user);
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    private class InternalServerErrorException extends RuntimeException {
    }

}
