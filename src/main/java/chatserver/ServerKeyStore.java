package chatserver;

import util.IKeyStore;

import java.security.PrivateKey;
import java.security.PublicKey;

public class ServerKeyStore implements IKeyStore {
    @Override
    public PrivateKey getPrivateKey(String username) {
        return null;
    }

    @Override
    public PublicKey getPublicKey(String username) {
        return null;
    }
}
