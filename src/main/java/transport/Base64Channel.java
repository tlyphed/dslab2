package transport;

import org.bouncycastle.util.encoders.Base64;

import java.io.IOException;

public class Base64Channel implements IChannel {

    private IChannel channel;

    public Base64Channel(IChannel channel){
        this.channel = channel;
    }

    @Override
    public void open() {
        channel.open();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    @Override
    public void write(String msg) throws IOException {
        channel.write(base64Encode(msg));
    }

    @Override
    public String read() throws IOException {
        return base64Decode(channel.read());
    }

    private String base64Encode(String msg) {
        if(msg == null) {
            return null;
        }
        return new String(Base64.encode(msg.getBytes()));
    }

    private String base64Decode(String msg) {
        if(msg == null) {
            return null;
        }
        return new String(Base64.decode(msg));
    }

}
