package util;

import java.io.File;
import java.io.IOException;
import java.security.PrivateKey;

public class PrivateKeyStore implements IPrivateKeyStore {

    private static final String PRV_KEY_SUFFIX = ".pem";

    private File keyDir;

    public PrivateKeyStore(File keyDir) throws IOException {
        this.keyDir = keyDir;
    }

    @Override
    public PrivateKey getPrivateKey(String username) throws IOException {
        File keyFile = new File(keyDir, username + PRV_KEY_SUFFIX);

        return Keys.readPrivatePEM(keyFile);
    }

}
