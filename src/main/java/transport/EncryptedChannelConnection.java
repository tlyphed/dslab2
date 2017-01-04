package transport;

import java.io.IOException;

public class EncryptedChannelConnection extends ChannelConnection {

    private EncryptedChannel channel;

    public EncryptedChannelConnection(EncryptedChannel channel){
        super(channel);
        this.channel = channel;
    }

    public void authenticate(String username) throws IOException {
        channel.authenticate(username);
        startReadingThread();
    }

    @Override
    protected void open() throws IOException {
        channel.open();
    }

    @Override
    public void writeToServer(String msg) throws IOException {
        if(channel.isAuthenticated()){
            super.writeToServer(msg);
        } else {
            fireResponseEvent("Authenticate first");
        }
    }

    @Override
    public void writeToServer(String msg, boolean silent, ResponseListener callback) throws IOException {
        if(channel.isAuthenticated()){
            super.writeToServer(msg, silent, callback);
        } else {
            fireResponseEvent("Authenticate first");
        }
    }
}
