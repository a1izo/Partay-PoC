package dk.via.partay.app.spotify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param mode live or mock */
@ConfigurationProperties(prefix = "spotify")
public record SpotifyProperties(String mode, String clientId, String clientSecret, String accountsUrl, String apiUrl) {

    public boolean hasCredentials() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
