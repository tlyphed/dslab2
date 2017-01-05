package chatserver;

import nameserver.INameserverForChatserver;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;

import java.rmi.RemoteException;

/**
 * Created by mkamleithner on 05.01.17.
 */
public class RegisterRemoteHelper {

    private final INameserverForChatserver root ;

    public RegisterRemoteHelper(INameserverForChatserver root) {
        this.root = root;
    }


    public void register(User user) throws InvalidDomainException, RemoteException, AlreadyRegisteredException {

        root.registerUser(user.getName(), user.getIpAddress());

    }
}
