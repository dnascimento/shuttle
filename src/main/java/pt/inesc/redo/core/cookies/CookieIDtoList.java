package pt.inesc.redo.core.cookies;

import java.util.HashMap;

public class CookieIDtoList {
    HashMap<String, CookieList> map = new HashMap<String, CookieList>();

    public CookieList get(String string) {
        CookieList list = map.get(string);
        if (list == null) {
            map.put(string, new CookieList());
            return map.get(string);
        }
        return list;
    }


}
