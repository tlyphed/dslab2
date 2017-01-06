package util;

import java.io.File;
import java.io.IOException;
import java.security.PublicKey;

public class PublicKeyStore implements IPublicKeyStore {

    private static final String PUB_KEY_SUFFIX = ".pub.pem";

    private File keyDir;

    public PublicKeyStore(File keyDir) {
        this.keyDir = keyDir;
    }

    @Override
    public PublicKey getPublicKey(String username) throws IOException {
        File keyFile = new File(keyDir, username + PUB_KEY_SUFFIX);

        return Keys.readPublicPEM(keyFile);
    }
}
