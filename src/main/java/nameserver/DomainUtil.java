package nameserver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by mkamleithner on 04.01.17.
 */
public final class DomainUtil {

    private DomainUtil(){
        //util class, do not create instances
    }


    public static List<String> splitDomainIntoParts(String domain){
        return new ArrayList<>(Arrays.asList(domain.split("\\.")));

    }


    public static String joinPartsToDomain(List<String> parts){

        String domain = "";
        for(String s : parts){
            domain += s + ".";
        }
        return domain.substring(0, domain.length()-1);

    }


    public static boolean userNameMatchesDomain(String username, String domain){

        List<String> userDomainPArts = DomainUtil.splitDomainIntoParts(username);
        String  userDomain = DomainUtil.joinPartsToDomain(userDomainPArts.subList(1, userDomainPArts.size()));
        //the username was just "something like "bob"
        return (userDomain.equals(domain));

    }

    public static String getUsernameFromFullyQualifiedName(String fullyQualifiedname){
        return DomainUtil.splitDomainIntoParts(fullyQualifiedname).get(0);
    }

    public static String lastPart(List<String> userName) {

        return userName.get(userName.size() -1);

    }

    public static String excludeLastPart(List<String> userName) {

        return joinPartsToDomain(userName.subList(0, userName.size() -1));

    }
}
