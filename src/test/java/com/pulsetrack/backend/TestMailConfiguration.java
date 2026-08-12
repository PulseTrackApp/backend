package com.pulsetrack.backend;

import com.pulsetrack.backend.user.RecordingResetCodeSender;
import com.pulsetrack.backend.user.RecordingVerificationCodeSender;
import com.pulsetrack.backend.user.ResetCodeSender;
import com.pulsetrack.backend.user.VerificationCodeSender;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Remplace l'acheminement des codes envoyes par courriel pendant les tests.
 *
 * <p>{@code @Primary} plutot qu'une exclusion : l'expediteur de repli reste
 * cree, ce qui verifie au passage que sa condition d'activation fonctionne, et
 * c'est celui-ci qui est injecte.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestMailConfiguration {

    @Bean
    @Primary
    ResetCodeSender recordingResetCodeSender() {
        return new RecordingResetCodeSender();
    }

    @Bean
    @Primary
    VerificationCodeSender recordingVerificationCodeSender() {
        return new RecordingVerificationCodeSender();
    }
}
