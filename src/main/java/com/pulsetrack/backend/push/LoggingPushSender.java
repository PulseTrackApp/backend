package com.pulsetrack.backend.push;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation de repli : journalise au lieu d'envoyer.
 *
 * <p>Active des que FCM n'est pas configure. Elle permet de developper et de
 * tester toute la chaine de rappels sans projet Firebase, et garantit qu'un
 * defaut de configuration ne fasse jamais echouer un traitement planifie.
 */
public class LoggingPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPushSender.class);

    @Override
    public boolean send(String token, PushNotification notification) {
        log.info("Notification non envoyee (FCM desactive) — destinataire {}, titre « {} », corps « {} »",
                PushTokens.masked(token), notification.title(), notification.body());
        return true;
    }
}
