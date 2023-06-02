
package quanta.model.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NostrMetadata {
    private String name;
    private String username;
    @JsonProperty("display_name")
    private String displayName;
    private String about;
    private String picture;
    private String banner;
    private String website;
    private String nip05;
    private boolean reactions;

    
    public String getName() {
        return this.name;
    }

    
    public String getUsername() {
        return this.username;
    }

    
    public String getDisplayName() {
        return this.displayName;
    }

    
    public String getAbout() {
        return this.about;
    }

    
    public String getPicture() {
        return this.picture;
    }

    
    public String getBanner() {
        return this.banner;
    }

    
    public String getWebsite() {
        return this.website;
    }

    
    public String getNip05() {
        return this.nip05;
    }

    
    public boolean isReactions() {
        return this.reactions;
    }

    
    public void setName(final String name) {
        this.name = name;
    }

    
    public void setUsername(final String username) {
        this.username = username;
    }

    @JsonProperty("display_name")
    
    public void setDisplayName(final String displayName) {
        this.displayName = displayName;
    }

    
    public void setAbout(final String about) {
        this.about = about;
    }

    
    public void setPicture(final String picture) {
        this.picture = picture;
    }

    
    public void setBanner(final String banner) {
        this.banner = banner;
    }

    
    public void setWebsite(final String website) {
        this.website = website;
    }

    
    public void setNip05(final String nip05) {
        this.nip05 = nip05;
    }

    
    public void setReactions(final boolean reactions) {
        this.reactions = reactions;
    }

    public NostrMetadata() {
    }
}
