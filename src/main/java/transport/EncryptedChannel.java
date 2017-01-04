package transport;

import org.bouncycastle.util.encoders.Base64;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.*;

public abstract class EncryptedChannel implements IChannel{

    public class AuthException extends IOException {
        public AuthException(Exception e){
            super(e);
        }
        public AuthException(String msg){
            super(msg);
        }
    }

    protected static final String ALGORITHM_AES = "AES/CTR/NoPadding";
    protected static final String ALGORITHM_RSA = "RSA/NONE/OAEPWithSHA256AndMGF1Padding";

    private IChannel channel;
    private boolean authenticated;
    private Cipher aesEncrypt, aesDecrypt;

    public EncryptedChannel(IChannel channel){
        if(channel == null){
            throw new IllegalArgumentException();
        }
        this.channel = channel;
    }



    protected String generateEncodedRandom(int size){
        SecureRandom secureRandom = new SecureRandom();
        final byte[] number = new byte[size];
        secureRandom.nextBytes(number);

        return new String(Base64.encode(number));
    }

    protected void setUpAES(SecretKeySpec secretKey, IvParameterSpec ivParameter) throws InvalidAlgorithmParameterException, InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        aesEncrypt = Cipher.getInstance(ALGORITHM_AES);
        aesEncrypt.init(Cipher.ENCRYPT_MODE, secretKey, ivParameter);

        aesDecrypt = Cipher.getInstance(ALGORITHM_AES);
        aesDecrypt.init(Cipher.DECRYPT_MODE, secretKey, ivParameter);
    }

    protected String encode(byte[] msg) {
        if(msg == null){
            return null;
        }
        return new String(Base64.encode(msg));
    }

    protected byte[] decode(String msg) {
        if(msg == null){
            return null;
        }
        return Base64.decode(msg);
    }

    protected String encrypt(String msg) throws BadPaddingException, IllegalBlockSizeException {
        if(msg == null){
            return null;
        }
        return encode(aesEncrypt.doFinal(msg.getBytes()));
    }

    protected String decrypt(String msg) throws BadPaddingException, IllegalBlockSizeException {
        if(msg == null){
            return null;
        }
        return new String(aesDecrypt.doFinal(decode(msg)));
    }


    @Override
    public void open() throws IOException {
        channel.open();
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

    protected void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }
}
