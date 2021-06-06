package com.mmmsys.m3vpn;

import java.util.HashMap;
import java.util.Map;

public class M3VPNConfig {
    private static M3VPNConfig queueInstance;

    public static M3VPNConfig getQueueInstance(){
        if(queueInstance==null){

            synchronized (M3VPNConfig.class){
                if(queueInstance==null)
                    queueInstance = new M3VPNConfig();
            }
        }
        return queueInstance;
    }


    private Map<Class<?>, Object> maps = new HashMap<>();
    private Map<Integer,Object> listMap = new HashMap<>();

    public <T> void push(T data) {

        maps.put(data.getClass(),data);

    }

    public <T> T pop(Class classType) {

        T value = (T) maps.get(classType);
        maps.remove(classType);

        return value;
    }
    public <T> void push(Integer message, T data) {

        listMap.put(message,data);

    }

    public <T> T pop(Integer message) {

        T value = (T) listMap.get(message);
        listMap.remove(message);
        return value;
    }

    public <T> T get(Integer message) {

        T value = (T) listMap.get(message);
        return value;
    }

    public <T>  Map<Integer, Object> getAll() {

        return listMap;
    }

}
