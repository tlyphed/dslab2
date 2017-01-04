package transport;

import org.bouncycastle.util.encoders.Base64;
import util.IPublicKeyStore;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.*;

public class EncryptedChannelServer extends EncryptedChannel {

    private IChannel channel;
    private PrivateKey chatserverPrivateKey;
    private IPublicKeyStore keyStore;

    public EncryptedChannelServer(IChannel channel, IPublicKeyStore keyStore, PrivateKey chatserverPrivateKey) {
        super(channel);
        this.channel = channel;
        this.chatserverPrivateKey = chatserverPrivateKey;
        this.keyStore = keyStore;
    }

    @Override
    public void open() throws IOException {
        super.open();

        PrivateKey privateKey = chatserverPrivateKey;

        while (!Thread.interrupted()) {
            try {
                Cipher rsa = Cipher.getInstance(ALGORITHM_RSA);
                rsa.init(Cipher.DECRYPT_MODE, privateKey);

                String authMsgCipher = channel.read();

                if (authMsgCipher == null) {
                    throw new IOException("Client socket closed");
                }

                String authMsgPlain = new String(rsa.doFinal(Base64.decode(authMsgCipher.getBytes())));

                String[] authMsgArgs = authMsgPlain.split(" ");

                if (authMsgArgs.length != 3 || !authMsgArgs[0].equals("!authenticate")) {
                    throw new AuthException("malformed client auth request");
                }

                String user = new String(Base64.decode(authMsgArgs[1]));

                PublicKey publicKey = keyStore.getPublicKey(user);

                if (publicKey == null) {
                    throw new AuthException("public key for '" + user + "' not found");
                }

                String clientChallenge = authMsgArgs[2];

                String serverChallenge = generateEncodedRandom(32);

                KeyGenerator generator = KeyGenerator.getInstance("ALGORITHM_AES");
                generator.init(32 * 8);
                SecretKey key = generator.generateKey();

                String secretKey = new String(Base64.encode(key.getEncoded()));
                String ivParameter = generateEncodedRandom(16);

                setUpAES(new SecretKeySpec(key.getEncoded(), "ALGORITHM_AES"), new IvParameterSpec(Base64.decode(ivParameter.getBytes())));

                rsa.init(Cipher.ENCRYPT_MODE, publicKey);

                String serverResponsePlain = "!ok " + clientChallenge + " " + serverChallenge + " " + secretKey + " " + ivParameter;
                String serverResponseCipher = new String(Base64.encode(rsa.doFinal(serverResponsePlain.getBytes())));

                channel.write(serverResponseCipher);

                String challengeCipher = channel.read();

                String challenge = decrypt(challengeCipher);

                if (!serverChallenge.equals(challenge)) {
                    throw new AuthException("server challenge doesn't match");
                }

                setAuthenticated(true);

                channel.write(encode("Success".getBytes()));

                break;

            } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | BadPaddingException |
                    IllegalBlockSizeException | InvalidAlgorithmParameterException e) {
                channel.write(encode("ERROR: Permission denied".getBytes()));
            } catch (AuthException e) {
                channel.write(encode("ERROR: Authentification failed".getBytes()));
            }
        }
    }
}
