/*
 * Author: Dario Nascimento (dario.nascimento@tecnico.ulisboa.pt)
 * 
 * Instituto Superior Tecnico - University of Lisbon - INESC-ID Lisboa
 * Copyright (c) 2014 - All rights reserved
 */
package pt.inesc.replay.core.cookies;

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
