package client;

import util.IKeyStore;

import java.security.PrivateKey;
import java.security.PublicKey;

public class ClientKeyStore implements IKeyStore {

    @Override
    public PrivateKey getPrivateKey(String username) {
        return null;
    }

    @Override
    public PublicKey getPublicKey(String username) {
        return null;
    }
}
