package chatserver;

import util.IKeyStore;
import util.Keys;

import java.io.File;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;

class ServerKeyStore implements IKeyStore {

    @Override
    public PrivateKey getPrivateKey(String username) throws IOException {
        return Keys.readPrivatePEM(new File("keys/chatserver/chatserver.pem"));
    }

    @Override
    public PublicKey getPublicKey(String username) throws IOException {
        return Keys.readPublicPEM(new File("keys/client/bill.de.pub.pem"));
    }
}
