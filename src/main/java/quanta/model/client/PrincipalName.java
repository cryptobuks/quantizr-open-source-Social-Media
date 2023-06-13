package quanta.model.client;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PrincipalName {
    // FollowBot is the account admins can use to "curate" the public
    // Fediverse feed.
    FOLLOW_BOT("FollowBot"), //

    ANON("anonymous"), //
    ADMIN("admin"), //
    PUBLIC("public");

    @JsonValue
    private final String value;

    private PrincipalName(String value) {
        this.value = value;
    }

    public String toString() {
        return value;
    }

    public String s() {
        return value;
    }
}
