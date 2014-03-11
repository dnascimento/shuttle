package pt.inesc.proxy.save;



public class DataSaver {
    enum SaveLocation {
        NO_SAVE, REMOTE, FILE
    }

    private static DataSaver instance = new DataSaver();
    private int lastStored = 0; // shared to know the last
    private final SaveLocation saveOpt = SaveLocation.FILE;

    public static DataSaver getInstance() {
        return instance;
    }

    public void save(int lastReplied) {
        int start;
        synchronized (instance) {
            if (lastStored > lastReplied)
                return;
            start = lastStored + 1;
            lastStored = lastReplied;
        }
        switch (saveOpt) {
        case FILE:
            new SaveFile(SaveType.Request, start, lastReplied).start();
            new SaveFile(SaveType.Response, start, lastReplied).start();
            break;
        case REMOTE:
            new SaveRemote(SaveType.Request, start, lastReplied, null).start();
            new SaveRemote(SaveType.Response, start, lastReplied, null).start();
            break;
        case NO_SAVE:
            break;
        default:
            break;
        }
    }
}
