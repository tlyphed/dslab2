package chatserver;

import util.AbstractTCPServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

public class TCPServer extends AbstractTCPServer {

    public TCPServer(int port){
        super(port);
    }

    private void send(String message, TCPWorker sender) throws IOException {
        for(TCPWorker worker : getWorkers()){
            if(worker != sender && worker.getOutput() != null){
                worker.getOutput().write("!pubmsg: " + message);
                worker.getOutput().newLine();
                worker.getOutput().flush();
            }
        }
    }

    protected void processInput(TCPWorker worker, BufferedReader in, BufferedWriter out) throws IOException {
        String line;
        User user = null;
        while((line = in.readLine()) != null && !Thread.currentThread().isInterrupted()){
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
                                        out.write("Successfully logged in.");
                                        out.newLine();
                                        out.flush();
                                        break;
                                    }
                                    out.write("Wrong username or password.");
                                    out.newLine();
                                    out.flush();
                                    break;
                                }
                                out.write("Already logged in.");
                                out.newLine();
                                out.flush();
                                break;
                            }
                        }
                        out.write("Unkown User.");
                        out.newLine();
                        out.flush();
                        break;
                    }
                    out.write("Wrong argument.");
                    out.newLine();
                    out.flush();
                    break;
                case "logout":
                    if(cmd.length == 1){
                        if(user != null){
                            user.setOnline(false);
                            user.setIpAddress(null);
                            user = null;
                            out.write("Successfully logged out.");
                            out.newLine();
                            out.flush();
                            break;
                        }
                        out.write("Not logged in.");
                        out.newLine();
                        out.flush();
                        break;
                    }
                    out.write("Wrong argument.");
                    out.newLine();
                    out.flush();
                    break;
                case "send":
                    if(cmd.length > 1){
                        if(user != null){
                            String msg = line.substring(line.indexOf(" ") + 1);
                            send(user.getName() + ": "+  msg, worker);
                            out.write("Message sent.");
                            out.newLine();
                            out.flush();
                            break;
                        }
                        out.write("Not logged in.");
                        out.newLine();
                        out.flush();
                        break;
                    }
                    out.write("Wrong argument.");
                    out.newLine();
                    out.flush();
                    break;
                case "register":
                    if(cmd.length == 2){
                        if(user != null){
                            String ipAddress = cmd[1];
                            user.setIpAddress(ipAddress);
                            out.write("Successfully registered.");
                            out.newLine();
                            out.flush();
                            break;
                        }
                        out.write("Not logged in.");
                        out.newLine();
                        out.flush();
                        break;
                    }
                    out.write("Wrong argument.");
                    out.newLine();
                    out.flush();
                    break;
                case "lookup":
                    if(cmd.length == 2){
                        if(user != null){
                            String lookupName = cmd[1];
                            User lookup = UserStore.getInstance().getUser(lookupName);
                            if(lookup != null){
                                if(lookup.getIpAddress() != null){
                                    out.write(lookup.getIpAddress());
                                    out.newLine();
                                    out.flush();
                                    break;
                                }
                            }
                            out.write("Wrong username or user not registered.");
                            out.newLine();
                            out.flush();
                            break;
                        }
                        out.write("Not logged in.");
                        out.newLine();
                        out.flush();
                        break;
                    }
                    out.write("Wrong argument.");
                    out.newLine();
                    out.flush();
                    break;
                case "exit":
                    worker.shutdown();
                    break;
                default:
                    out.write("Wrong argument.");
                    out.newLine();
                    out.flush();
            }
        }
        if(user != null){
            user.setOnline(false);
            user.setIpAddress(null);
        }
    }

}
