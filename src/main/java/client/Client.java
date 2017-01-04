package client;

import cli.Command;
import cli.Shell;
import transport.*;
import util.Config;
import util.IPrivateKeyStore;
import util.Keys;
import util.PrivateKeyStore;

import java.io.*;
import java.net.*;
import java.security.PublicKey;

public class Client implements IClientCli, Runnable {

    private String componentName;
    private Config config;
    private InputStream userRequestStream;
    private PrintStream userResponseStream;

    private Shell shell;

    private EncryptedChannelConnection channelConnection;

    private int tcpPort;
    private int udpPort;

    private String lastMsg;

    private AbstractTCPServer tcpServer;

    private String username = null;

    /**
     * @param componentName      the name of the component - represented in the prompt
     * @param config             the configuration to use
     * @param userRequestStream  the input stream to read user input from
     * @param userResponseStream the output stream to write the console output to
     */
    public Client(String componentName, Config config, InputStream userRequestStream, PrintStream userResponseStream) {
        this.componentName = componentName;
        this.config = config;
        this.userRequestStream = userRequestStream;
        this.userResponseStream = userResponseStream;

        tcpPort = config.getInt("chatserver.tcp.port");
        udpPort = config.getInt("chatserver.udp.port");

        shell = new Shell(componentName, userRequestStream, userResponseStream);
        shell.register(this);
    }

    @Override
    public void run() {
        Thread.currentThread().setName("ClientThread");

        try {
            IPrivateKeyStore keyStore = new PrivateKeyStore(new File(config.getString("keys.dir")));
            PublicKey chatserverKey = Keys.readPublicPEM(new File(config.getString("chatserver.key")));

            channelConnection = new EncryptedChannelConnection(new EncryptedChannelClient(new TCPChannel("localhost", tcpPort), keyStore, chatserverKey));
            channelConnection.setResponseListener(new ChannelConnection.ResponseListener() {
                @Override
                public void onResponse(String response) {
                    Client.this.onResponse(response);
                }
            });

            Thread shellThread = new Thread(shell);
            shellThread.setName("ShellThread");
            shellThread.start();

            Thread channelConnectionThread = new Thread(channelConnection);
            channelConnectionThread.setName("ChannelConnectionThread");
            channelConnectionThread.start();

            try {
                channelConnectionThread.join();
            } catch (InterruptedException e) {
            }


            exit();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void onResponse(String msg) {
        try {
            if (msg.startsWith("!pubmsg: ")) {
                lastMsg = msg.substring(msg.indexOf(":") + 2);
                shell.writeLine(lastMsg);
            } else if (msg.equals("exit")) {
                exit();
            } else {
                shell.writeLine(msg);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    @Command
    public void login(final String username, String password) throws IOException {
        channelConnection.writeToServer("login " + username + " " + password, false, new ChannelConnection.ResponseListener() {
            @Override
            public void onResponse(String response) {
                if (response.equals("Successfully logged in.")) {
                    Client.this.username = username;
                }
            }
        });
    }

    @Override
    @Command
    public void logout() throws IOException {
        channelConnection.writeToServer("logout");
        username = null;
        tcpServer.shutdown();
    }

    @Override
    @Command
    public void send(String message) throws IOException {
        channelConnection.writeToServer("send " + message);
    }

    @Override
    @Command
    public void list() throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("UDPThread");
                try {
                    try (DatagramSocket socket = new DatagramSocket()) {
                        byte[] buf = "list".getBytes();
                        InetAddress address = InetAddress.getLocalHost();
                        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, udpPort);
                        socket.setSoTimeout(20000);
                        socket.send(packet);

                        byte[] response = new byte[256];
                        packet = new DatagramPacket(response, response.length);
                        socket.receive(packet);
                        String received = new String(packet.getData(), 0, packet.getLength());
                        shell.writeLine("Online users:");
                        shell.writeLine(received);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    @Override
    @Command
    public void msg(final String username, final String message) throws IOException {
        channelConnection.writeToServer("lookup " + username, true, new ChannelConnection.ResponseListener() {
            @Override
            public void onResponse(String response) {
                try {
                    if (response.equals("Wrong username or user not registered.")) {
                        shell.writeLine("Wrong username or user not reachable.");
                    } else if (response.equals("Not logged in.")) {
                        shell.writeLine("Not logged in.");
                    } else {
                        String addr[] = response.split(":");

                        try (Socket socket = new Socket(addr[0], Integer.parseInt(addr[1]));
                             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

                            out.write(username + ": " + message);
                            out.newLine();
                            out.flush();

                            String ack = in.readLine();
                            if (ack.equals("!ack")) {
                                shell.writeLine(username + " replied with ack!");
                            }
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    @Command
    public void lookup(String username) throws IOException {
        channelConnection.writeToServer("lookup " + username);
    }

    @Override
    @Command
    public void register(final String privateAddress) throws IOException {
        try {
            String addr[] = privateAddress.split(":");
            InetAddress.getByName(addr[0]);
            final int port = Integer.parseInt(addr[1]);
            if (!AbstractTCPServer.checkPort(port)) {
                shell.writeLine("Port already used.");
                return;
            }
            channelConnection.writeToServer("register " + privateAddress, false, new ChannelConnection.ResponseListener() {
                @Override
                public void onResponse(String response) {
                    if (response.equals("Successfully registered.")) {
                        try {
                            if (tcpServer != null) {
                                tcpServer.shutdown();
                            }
                            tcpServer = new AbstractTCPServer(port) {
                                @Override
                                protected void processInput(TCPWorker worker, IChannel channel) throws IOException {
                                    String line = channel.read();
                                    shell.writeLine(line);
                                    channel.write("!ack");
                                }
                            };
                            Thread listeningThread = new Thread(tcpServer);
                            listeningThread.setName("PrivateListeningThread");
                            listeningThread.start();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        } catch (UnknownHostException | NumberFormatException | ArrayIndexOutOfBoundsException e) {
            shell.writeLine("Wrong Address Format.");
        }
    }

    @Override
    @Command
    public String lastMsg() throws IOException {
        if (lastMsg != null) {
            return lastMsg;
        }

        return "No message received!";
    }

    @Override
    @Command
    public void exit() throws IOException {
        channelConnection.close();
        if (tcpServer != null) {
            tcpServer.shutdown();
        }

        shell.close();
        userRequestStream.close();
        userResponseStream.close();
    }

    /**
     * @param args the first argument is the name of the {@link Client} component
     */
    public static void main(String[] args) {
        String name = "client";
        if (args.length > 0) {
            name = args[0];
        }
        Client client = new Client(name, new Config("client"), System.in, System.out);
        new Thread(client).start();
    }

    @Override
    @Command
    public String authenticate(String username) throws IOException {
        try {
            channelConnection.authenticate(username);

            return "Success";
        } catch (EncryptedChannel.AuthException e) {
            return e.getMessage();
        }
    }

}
