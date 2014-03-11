package pt.inesc.manager.redo.cookies;

import java.util.HashMap;

public class CookieList {
    // Original, New
    HashMap<String, String> map = new HashMap<String, String>();
    String pendentOriginal;
    String pendentNew;

    // Original pode ser null e haver newValue
    public void addCookie(String originalCook, String newValue) {
        if (originalCook == null) {
            if (pendentOriginal == null) {
                pendentNew = newValue;
            } else {
                map.put(pendentOriginal, newValue);
                pendentOriginal = null;
            }
        } else {
            map.put(originalCook, newValue);
        }
    }

    /**
     * Try to convert or add as default
     * 
     * @param string
     * @return
     */
    public String get(String original) {
        String newValue = map.get(original);
        if (newValue == null) {
            if (pendentNew == null) {
                pendentOriginal = original;
                return original;
            } else {
                map.put(original, pendentNew);
                newValue = pendentNew;
                pendentNew = null;
            }
        }
        return newValue;
    }
}
