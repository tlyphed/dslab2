package chatserver;

import transport.AbstractTCPServer;
import transport.Base64Channel;
import transport.EncryptedChannel;
import transport.IChannel;

import java.io.IOException;
import java.net.Socket;

public class TCPServer extends AbstractTCPServer {

    public TCPServer(int port){
        super(port);
    }

    private void send(String message, TCPWorker sender) throws IOException {
        for(TCPWorker worker : getWorkers()){
            if(worker != sender && worker.getChannel() != null){
                worker.getChannel().write("!pubmsg: " + message);
            }
        }
    }

    @Override
    protected IChannel wrapSocket(Socket socket) {
        return new Base64Channel(new EncryptedChannel(super.wrapSocket(socket), EncryptedChannel.Mode.SERVER));
    }

    protected void processInput(TCPWorker worker, IChannel channel) throws IOException {
        String line;
        User user = null;
        while((line = channel.read()) != null && !Thread.currentThread().isInterrupted()){
            String cmd[] = line.split(" ");
            switch(cmd[0]){
                case "login":
                    if(cmd.length == 3){
                        String name = cmd[1];
                        String password = cmd[2];
                        user = UserStore.getInstance().getUser(name);
                        if(user != null){
                            synchronized (user){
                                if(!user.isOnline()){
                                    if(user.getPassword().equals(password)){
                                        user.setOnline(true);
                                        channel.write("Successfully logged in.");
                                        break;
                                    }
                                    channel.write("Wrong username or password.");
                                    break;
                                }
                                channel.write("Already logged in.");
                                break;
                            }
                        }
                        channel.write("Unkown User.");
                        break;
                    }
                    channel.write("Wrong argument.");
                    break;
                case "logout":
                    if(cmd.length == 1){
                        if(user != null){
                            user.setOnline(false);
                            user.setIpAddress(null);
                            user = null;
                            channel.write("Successfully logged out.");
                            break;
                        }
                        channel.write("Not logged in.");
                        break;
                    }
                    channel.write("Wrong argument.");
                    break;
                case "send":
                    if(cmd.length > 1){
                        if(user != null){
                            String msg = line.substring(line.indexOf(" ") + 1);
                            send(user.getName() + ": "+  msg, worker);
                            channel.write("Message sent.");
                            break;
                        }
                        channel.write("Not logged in.");
                        break;
                    }
                    channel.write("Wrong argument.");
                    break;
                case "register":
                    if(cmd.length == 2){
                        if(user != null){
                            String ipAddress = cmd[1];
                            user.setIpAddress(ipAddress);
                            channel.write("Successfully registered.");
                            break;
                        }
                        channel.write("Not logged in.");
                        break;
                    }
                    channel.write("Wrong argument.");
                    break;
                case "lookup":
                    if(cmd.length == 2){
                        if(user != null){
                            String lookupName = cmd[1];
                            User lookup = UserStore.getInstance().getUser(lookupName);
                            if(lookup != null){
                                if(lookup.getIpAddress() != null){
                                    channel.write(lookup.getIpAddress());
                                    break;
                                }
                            }
                            channel.write("Wrong username or user not registered.");
                            break;
                        }
                        channel.write("Not logged in.");
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
        if(user != null){
            user.setOnline(false);
            user.setIpAddress(null);
        }
    }

}
