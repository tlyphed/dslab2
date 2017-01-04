package util;

import java.io.IOException;
import java.security.PublicKey;

public interface IPublicKeyStore {

    PublicKey getPublicKey(String username) throws IOException;

}
