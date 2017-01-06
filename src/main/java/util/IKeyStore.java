package util;

import java.io.IOException;
import java.security.PrivateKey;
import java.security.PublicKey;

public interface IKeyStore {

    PrivateKey getPrivateKey(String username) throws IOException;

    PublicKey getPublicKey(String username) throws IOException;

}
