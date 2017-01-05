package transport;

import org.bouncycastle.util.encoders.Base64;

import javax.crypto.Mac;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

public class HmacChannel implements IChannel {

    public class MessageTamperedException extends IOException{

        private String tamperedMsg;

        public MessageTamperedException(String errorMsg, String tamperedMsg){
            super(errorMsg);
            this.tamperedMsg = tamperedMsg;
        }

        public String getTamperedMsg() {
            return tamperedMsg;
        }
    }

    private IChannel channel;
    private Key secretKey;

    public HmacChannel(IChannel channel, Key secretKey){
        this.channel = channel;
        this.secretKey = secretKey;
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
        channel.write(hmac(msg) + " " + msg);
    }

    @Override
    public String read() throws IOException {
        String msg = channel.read();
        if(msg == null){
            return null;
        }

        String hmac = msg.substring(0, msg.indexOf(' '));
        String payload = msg.substring(msg.indexOf(' ') + 1);

        if(!hmac.equals(hmac(payload))){
            throw new MessageTamperedException("message tampered with", payload);
        }

        return payload;
    }

    private String hmac(String msg) throws IOException {
        Mac hMac = null;
        try {
            hMac = Mac.getInstance("HmacSHA256");
            hMac.init(secretKey);
            hMac.update(msg.getBytes());
            byte[] hash = hMac.doFinal();

            return new String(Base64.encode(hash));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IOException(e);
        }

    }
}
