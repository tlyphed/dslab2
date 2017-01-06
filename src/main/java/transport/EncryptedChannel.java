package transport;

import org.bouncycastle.util.encoders.Base64;
import util.IKeyStore;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.*;

public class EncryptedChannel implements IChannel{

    public class AuthException extends IOException {
        public AuthException(Exception e){
            super(e);
        }
        public AuthException(String msg){
            super(msg);
        }
    }

    public enum Mode {
        SERVER, CLIENT
    }

    private static final String AES = "AES/CTR/NoPadding";
    private static final String RSA = "RSA/NONE/OAEPWithSHA256AndMGF1Padding";

    private IChannel channel;
    private boolean authenticated;
    private Mode mode;
    private IKeyStore keyStore;
    private Cipher aesEncrypt, aesDecrypt;

    public EncryptedChannel(IChannel channel, Mode mode, IKeyStore keyStore){
        if(channel == null || mode == null){
            throw new IllegalArgumentException();
        }
        this.channel = channel;
        this.mode = mode;
        this.keyStore = keyStore;
    }

    public void authenticate(String user) throws IOException, AuthException{
        if(mode == Mode.SERVER){
            throw new IllegalStateException("Channel is in SERVER mode");
        }
        PrivateKey privateKey = keyStore.getPrivateKey(user);
        PublicKey publicKey = keyStore.getPublicKey("chatserver");

        if(privateKey == null || publicKey == null) {
            throw new AuthException("Key not found");
        }

        String challenge = generateEncodedRandom(32);

        String plainText = "!authenticate " + user + " " + challenge;

        try {
            Cipher rsa = Cipher.getInstance(RSA);

            rsa.init(Cipher.ENCRYPT_MODE, publicKey);

            String cipherText = new String(Base64.encode(rsa.doFinal(plainText.getBytes())));

            channel.write(cipherText);

            String serverResponseCipher = channel.read();

            if(serverResponseCipher == null) {
                throw new AuthException("server response error");
            }

            rsa.init(Cipher.DECRYPT_MODE, privateKey);

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
            SecretKeySpec secretKey = new SecretKeySpec(Base64.decode(serverResponseArgs[3].getBytes()), "AES");
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

        authenticated = true;
    }

    private String generateEncodedRandom(int size){
        SecureRandom secureRandom = new SecureRandom();
        final byte[] number = new byte[size];
        secureRandom.nextBytes(number);

        return new String(Base64.encode(number));
    }

    private void setUpAES(SecretKeySpec secretKey, IvParameterSpec ivParameter) throws InvalidAlgorithmParameterException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        aesEncrypt = Cipher.getInstance(AES);
        aesEncrypt.init(Cipher.ENCRYPT_MODE, secretKey, ivParameter);

        aesDecrypt = Cipher.getInstance(AES);
        aesDecrypt.init(Cipher.DECRYPT_MODE, secretKey, ivParameter);
    }

    private String encode(byte[] msg) {
        if(msg == null){
            return null;
        }
        return new String(Base64.encode(msg));
    }

    private byte[] decode(String msg) {
        if(msg == null){
            return null;
        }
        return Base64.decode(msg);
    }

    private String encrypt(String msg) throws BadPaddingException, IllegalBlockSizeException {
        if(msg == null){
            return null;
        }
        return encode(aesEncrypt.doFinal(msg.getBytes()));
    }

    private String decrypt(String msg) throws BadPaddingException, IllegalBlockSizeException {
        if(msg == null){
            return null;
        }
        return new String(aesDecrypt.doFinal(decode(msg)));
    }


    @Override
    public void open() throws IOException {
        channel.open();

        if(mode == Mode.SERVER){
            PrivateKey privateKey = keyStore.getPrivateKey("chatserver");

            while(!Thread.interrupted()){
                try {
                    Cipher rsa = Cipher.getInstance(RSA);
                    rsa.init(Cipher.DECRYPT_MODE, privateKey);

                    String authMsgCipher = channel.read();

                    if(authMsgCipher == null) {
                        throw new IOException("Client socket closed");
                    }

                    String authMsgPlain = new String(rsa.doFinal(Base64.decode(authMsgCipher.getBytes())));

                    String[] authMsgArgs = authMsgPlain.split(" ");

                    if(authMsgArgs.length != 3 || !authMsgArgs[0].equals("!authenticate")){
                        throw new AuthException("malformed client auth request");
                    }

                    String user = new String(Base64.decode(authMsgArgs[1]));

                    PublicKey publicKey = keyStore.getPublicKey(user);

                    if(publicKey == null){
                        throw new AuthException("public key for '" + user + "' not found");
                    }

                    String clientChallenge = authMsgArgs[2];

                    String serverChallenge = generateEncodedRandom(32);

                    KeyGenerator generator = KeyGenerator.getInstance("AES");
                    generator.init(32 * 8);
                    SecretKey key = generator.generateKey();

                    String secretKey = new String(Base64.encode(key.getEncoded()));
                    String ivParameter = generateEncodedRandom(16);

                    setUpAES(new SecretKeySpec(key.getEncoded(), "AES"), new IvParameterSpec(Base64.decode(ivParameter.getBytes())));

                    rsa.init(Cipher.ENCRYPT_MODE, publicKey);

                    String serverResponsePlain = "!ok " + clientChallenge + " " + serverChallenge + " " + secretKey + " " + ivParameter;
                    String serverResponseCipher = new String(Base64.encode(rsa.doFinal(serverResponsePlain.getBytes())));

                    channel.write(serverResponseCipher);

                    String challengeCipher = channel.read();

                    String challenge = decrypt(challengeCipher);

                    if(!serverChallenge.equals(challenge)){
                        throw new AuthException("server challenge doesn't match");
                    }

                    authenticated = true;

                    channel.write(encode("Success".getBytes()));

                    break;

                } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException | BadPaddingException |
                        IllegalBlockSizeException | InvalidAlgorithmParameterException e) {
                    channel.write(encode("Permission denied".getBytes()));
                } catch (AuthException e) {
                    channel.write(encode("Authentification failed".getBytes()));
                }

            }


        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public void write(String msg) throws IOException {
        if(!authenticated){
            channel.write(encode(msg.getBytes()));
        } else {
            try {
                channel.write(encrypt(msg));
            } catch (BadPaddingException | IllegalBlockSizeException e) {
                throw new IOException("encryption error", e);
            }
        }
    }

    @Override
    public String read() throws IOException {
        if(!authenticated){
            return new String(decode(channel.read()));
        } else {
            try {
                return decrypt(channel.read());
            } catch (BadPaddingException | IllegalBlockSizeException e) {
                throw new IOException("decryption error", e);
            }
        }
    }

    public boolean isAuthenticated() {
        return authenticated;
    }
}
