package chatserver;

import nameserver.INameserverForChatserver;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import transport.AbstractTCPServer;
import transport.EncryptedChannel;
import transport.IChannel;

import java.io.IOException;
import java.net.Socket;

public class TCPServer extends AbstractTCPServer {

    private final LookupRemoteHelper lookupRemoteHelper;
    private RegisterRemoteHelper registerHelper;
    private EncryptedChannelServer channel;

    public TCPServer(int port, INameserverForChatserver root) {
        super(port);
        this.lookupRemoteHelper = new LookupRemoteHelper(root);
        this.registerHelper = new RegisterRemoteHelper(root);
    }

    private void send(String message, TCPWorker sender) throws IOException {
        for (TCPWorker worker : getWorkers()) {
            if (worker != sender && worker.getChannel() != null) {
                worker.getChannel().write("!pubmsg: " + message);
            }
        }
    }

    @Override
    protected IChannel wrapSocket(Socket socket) {
        return new EncryptedChannel(super.wrapSocket(socket), EncryptedChannel.Mode.SERVER, new ServerKeyStore());
    }

    protected void processInput(TCPWorker worker, IChannel channel) throws IOException {
        String line;
        User user = UserStore.getInstance().getUser(this.channel.getUsername());
        if (user == null) {
            throw new IOException("user unknown");
        }
        user.setOnline(true);
        while ((line = channel.read()) != null && !Thread.currentThread().isInterrupted()) {
            String cmd[] = line.split(" ");
            switch (cmd[0]) {
                case "send":
                    if (cmd.length > 1) {

                        String msg = line.substring(line.indexOf(" ") + 1);
                        send(user.getName() + ": " + msg, worker);
                        channel.write("Message sent.");
                        break;

                    }
                    channel.write("Wrong argument.");
                    break;
                case "register":
                    if (cmd.length == 2) {
                        String ipAddress = cmd[1];
                        user.setIpAddress(ipAddress);
                        channel.write("Successfully registered.");
                        try {
                            registerHelper.register(user);
                            channel.write("Successfully registered.");
                        } catch (InvalidDomainException e) {
                            channel.write("Invalid Domain!");
                        } catch (AlreadyRegisteredException a) {
                            channel.write("Already registered!");
                        }
                        break;
                    }
                    channel.write("Wrong argument.");
                    break;
                case "lookup":
                    if (cmd.length == 2) {
                        String lookupName = cmd[1];
                        String lookupAddress = lookupRemoteHelper.lookupAddress(lookupName);
                        if (lookupAddress != null) {
                            channel.write(lookupAddress);
                            break;
                        }
                        channel.write("Wrong username or user not registered.");
                        break;
                    }
                    channel.write("Wrong argument.");
                    break;
                case "exit":
                    worker.shutdown();
                    break;
                default:
                    channel.write("Wrong argument.");
            }
        }
        if (user != null) {
            user.setOnline(false);
            user.setIpAddress(null);
        }
    }

}
