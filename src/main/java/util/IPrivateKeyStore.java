package util;

import java.io.IOException;
import java.security.PrivateKey;

public interface IPrivateKeyStore {

    PrivateKey getPrivateKey(String username) throws IOException;

}
