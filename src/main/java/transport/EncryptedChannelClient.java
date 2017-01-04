package transport;

import org.bouncycastle.util.encoders.Base64;
import util.IPrivateKeyStore;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.*;

public class EncryptedChannelClient extends EncryptedChannel{

    private IChannel channel;
    private PublicKey chatserverPubKey;
    private IPrivateKeyStore keyStore;

    public EncryptedChannelClient(IChannel channel, IPrivateKeyStore keyStore, PublicKey chatserverPubKey) {
        super(channel);
        this.channel = channel;
        this.chatserverPubKey = chatserverPubKey;
        this.keyStore = keyStore;
    }

    public void authenticate(String user) throws IOException {
        PrivateKey privateKey = keyStore.getPrivateKey(user);
        PublicKey publicKey = chatserverPubKey;

        if(privateKey == null || publicKey == null) {
            throw new AuthException("Key not found");
        }

        String challenge = generateEncodedRandom(32);

        String plainText = "!authenticate " + user + " " + challenge;

        try {
            Cipher rsa = Cipher.getInstance(ALGORITHM_RSA);

            rsa.init(Cipher.ENCRYPT_MODE, publicKey);

            String cipherText = new String(Base64.encode(rsa.doFinal(plainText.getBytes())));

            channel.write(cipherText);

            String serverResponseCipher = channel.read();

            if(serverResponseCipher == null) {
                throw new AuthException("server response error");
            }

            rsa.init(Cipher.DECRYPT_MODE, privateKey);

            String serverResponseDecoded = new String(Base64.decode(serverResponseCipher.getBytes()));

            if(serverResponseDecoded.startsWith("ERROR: ")){
                throw new AuthException(serverResponseDecoded);
            }

            String serverResponsePlain = new String(rsa.doFinal(Base64.decode(serverResponseCipher.getBytes())));

            String[] serverResponseArgs = serverResponsePlain.split(" ");

            if(serverResponseArgs.length != 5 || !serverResponseArgs[0].equals("!ok")){
                throw new AuthException("malformed server response");
            }

            String clientChallenge = serverResponseArgs[1];

            if(!clientChallenge.equals(challenge)){
                throw new AuthException("client challenge doesn't match");
            }

            String chatserverChallenge = serverResponseArgs[2];
            SecretKeySpec secretKey = new SecretKeySpec(Base64.decode(serverResponseArgs[3].getBytes()), "ALGORITHM_AES");
            IvParameterSpec ivParameter = new IvParameterSpec(Base64.decode(serverResponseArgs[4].getBytes()));

            setUpAES(secretKey, ivParameter);

            String clientResponse = encrypt(chatserverChallenge);

            channel.write(clientResponse);

            String serverCheck = new String(decode(channel.read()));

            if(!serverCheck.equals("Success")){
                throw new AuthException("auth failed");
            }

        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException | NoSuchPaddingException |
                InvalidKeyException | BadPaddingException | IllegalBlockSizeException e) {
            throw new AuthException(e);
        }

        setAuthenticated(true);
    }
}
