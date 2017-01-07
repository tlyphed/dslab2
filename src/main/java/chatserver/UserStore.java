package chatserver;

import util.Config;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserStore {

    private static UserStore instance;
    private ConcurrentHashMap<String, User> userMap;

    private UserStore(){
        userMap = new ConcurrentHashMap<>();
    }

    public synchronized static UserStore getInstance(){
        if(instance == null){
            instance = new UserStore();
        }

        return instance;
    }

    public synchronized void loadFromFile(){
        Config config = new Config("user");
        for(String key : config.listKeys()){
            String name = key.substring(0, key.lastIndexOf("."));
            String password = config.getString(key);
            User user = new User();
            user.setName(name);
            user.setPassword(password);
            userMap.put(name, user);
        }
    }

    public synchronized User getUser(String name){
        System.out.println("get name " + name);
        return userMap.get(name);
    }

    public synchronized Collection<User> listUsers(){
        List<User> users = new ArrayList<>(userMap.values());
        Collections.sort(users, new Comparator<User>() {
            @Override
            public int compare(User o1, User o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        return users;
    }

}
