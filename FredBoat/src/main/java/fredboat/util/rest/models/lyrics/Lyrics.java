package fredboat.util.rest.models.lyrics;

public class Lyrics {
    private String identifier;
    private String title;
    private String author;
    private String lyrics;
    private String geniusLyricsKey;

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getLyrics() {
        return lyrics;
    }

    public void setLyrics(String lyrics) {
        this.lyrics = lyrics;
    }

    public String getGeniusLyricsKeyl() {
        return geniusLyricsKey;
    }

    public void setGeniusLyricsKey(String geniusLyricsKey) {
        this.geniusLyricsKey = geniusLyricsKey;
    }
}
