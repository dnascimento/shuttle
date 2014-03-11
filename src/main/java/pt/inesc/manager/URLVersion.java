package pt.inesc.manager;

public class URLVersion {
    public String url;
    public String version;

    public URLVersion(String url, String version) {
        super();
        this.url = url;
        this.version = version;
    }

    @Override
    public String toString() {
        return "[" + url + ":" + version + "]";
    }





}
