package chatserver;


import nameserver.DomainUtil;
import nameserver.INameserverForChatserver;

import java.rmi.RemoteException;
import java.util.Collections;
import java.util.List;

/**
 * Created by mkamleithner on 05.01.17.
 */
public class LookupRemoteHelper {

    private final INameserverForChatserver rootNs;

    public LookupRemoteHelper(INameserverForChatserver rootNs) {
        this.rootNs = rootNs;
    }


    public String lookupAddress(String username) throws RemoteException {

        List<String> domainParts = DomainUtil.splitDomainIntoParts(username);

        if(domainParts.size() == 0){
            return null;
        }
        Collections.reverse(domainParts);

        INameserverForChatserver chatserverToAsk = rootNs;

        for(int i = 0; i< domainParts.size() -1; i ++){
            chatserverToAsk = chatserverToAsk.getNameserver(domainParts.get(i));
            if(chatserverToAsk == null){
                return null;
            }
        }



        return chatserverToAsk.lookup(domainParts.get(domainParts.size() -1));


    }
}
