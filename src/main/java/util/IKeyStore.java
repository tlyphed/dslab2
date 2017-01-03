package util;

import java.security.PrivateKey;
import java.security.PublicKey;

public interface IKeyStore {

    PrivateKey getPrivateKey(String username);

    PublicKey getPublicKey(String username);

}
