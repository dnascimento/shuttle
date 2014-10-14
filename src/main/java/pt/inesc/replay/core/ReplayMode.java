package pt.inesc.replay.core;

public enum ReplayMode {
    allSerial, allParallel, selectiveSerial, selectiveParallel;

    public static ReplayMode castFromInt(int opt) throws Exception {
        switch (opt) {
        case 0:
            return ReplayMode.allSerial;
        case 1:
            return ReplayMode.allParallel;
        case 2:
            return ReplayMode.selectiveSerial;
        case 3:
            return ReplayMode.selectiveParallel;
        default:
            throw new Exception("Unknown Replay mode");
        }


    }
}
