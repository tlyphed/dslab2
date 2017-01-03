package util;

import java.io.IOException;

public class EncryptedChannel implements IChannel{

    private IChannel channel;
    private boolean authenticated;

    public EncryptedChannel(IChannel channel){
        this.channel = channel;
    }

    public void authenticate(String user) {
        authenticated = true;
        // TODO
    }

    private String encrypt(String msg){
        // TODO
        return msg;
    }

    private String decrypt(String msg){
        // TODO
        return msg;
    }


    @Override
    public void open() {
        if(!authenticated){
            throw new IllegalStateException("authenticate first!");
        }

        channel.open();
    }

    @Override
    public void close() throws IOException {
        if(!authenticated){
            throw new IllegalStateException("authenticate first!");
        }

        channel.close();
    }

    @Override
    public void write(String msg) throws IOException {
        if(!authenticated){
            throw new IllegalStateException("authenticate first!");
        }

        channel.write(encrypt(msg));
    }

    @Override
    public String read() throws IOException {
        if(!authenticated){
            throw new IllegalStateException("authenticate first!");
        }

        return decrypt(channel.read());
    }
}
