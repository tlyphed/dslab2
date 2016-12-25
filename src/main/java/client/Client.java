package client;

import cli.Command;
import cli.Shell;
import util.AbstractTCPServer;
import util.Config;

import java.io.*;
import java.net.*;
import java.util.concurrent.Semaphore;

public class Client implements IClientCli, Runnable {

    private String componentName;
    private Config config;
    private InputStream userRequestStream;
    private PrintStream userResponseStream;

    private Shell shell;

    private int tcpPort;
    private int udpPort;

    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;

    private String lastResponse, lastMsg;

    private final Semaphore available = new Semaphore(1, true);

    private boolean silentMode = false;

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

        Thread shellThread = new Thread(shell);
        shellThread.setName("ShellThread");
        shellThread.start();

        try (Socket socket = new Socket("localhost", tcpPort);
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            this.socket = socket;
            this.out = out;
            this.in = in;

            Thread readingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Thread.currentThread().setName("ReadingThread");
                    try {
                        processInput(in, out);
                    } catch (IOException | InterruptedException e) {
                        if (!(e.getMessage().equals("Stream closed") || e.getMessage().equals("Socket closed"))) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            readingThread.start();

            try {
                readingThread.join();
            } catch (InterruptedException e) {
            }

            exit();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processInput(BufferedReader in, BufferedWriter out) throws IOException, InterruptedException {
        String line;
        while (!Thread.currentThread().isInterrupted()) {
            available.acquire();
            if ((line = in.readLine()) != null) {
                if (line.startsWith("!pubmsg: ")) {
                    lastMsg = line.substring(line.indexOf(":") + 2);
                    shell.writeLine(lastMsg);
                } else if (line.equals("exit")) {
                    exit();
                } else {
                    if (!silentMode) {
                        shell.writeLine(line);
                    }
                    lastResponse = line;
                }
            } else {
                break;
            }
            available.release();
        }
    }

    private void writeToServer(String msg) throws IOException {
        out.write(msg);
        out.newLine();
        out.flush();
    }

    private void writeToServer(String msg, final boolean silent, final Callback callback) throws IOException {
        silentMode = silent;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("ServerRequestThread");
                try {
                    available.acquire();
                    silentMode = false;
                    String response = lastResponse;
                    available.release();
                    callback.onResponse(response);
                } catch (InterruptedException e) {
                }
            }
        }).start();
        writeToServer(msg);
    }

    @Override
    @Command
    public void login(final String username, String password) throws IOException {
        writeToServer("login " + username + " " + password, false, new Callback() {
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
        writeToServer("logout");
        username = null;
        tcpServer.shutdown();
    }

    @Override
    @Command
    public void send(String message) throws IOException {
        writeToServer("send " + message);
    }

    @Override
    @Command
    public void list() throws IOException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("UPDThread");
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
        writeToServer("lookup " + username, true, new Callback() {
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
        writeToServer("lookup " + username);
    }

    @Override
    @Command
    public void register(final String privateAddress) throws IOException {
        try {
            String addr[] = privateAddress.split(":");
            InetAddress.getByName(addr[0]);
            final int port = Integer.parseInt(addr[1]);
            if(!AbstractTCPServer.checkPort(port)){
                shell.writeLine("Port already used.");
                return;
            }
            writeToServer("register " + privateAddress, false, new Callback() {
                @Override
                public void onResponse(String response) {
                    if (response.equals("Successfully registered.")) {
                        try {
                            if (tcpServer != null) {
                                tcpServer.shutdown();
                            }
                            tcpServer = new AbstractTCPServer(port) {
                                @Override
                                protected void processInput(TCPWorker worker, BufferedReader in, BufferedWriter out) throws IOException {
                                    String line = in.readLine();
                                    shell.writeLine(line);
                                    out.write("!ack");
                                    out.newLine();
                                    out.flush();
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
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
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

    // --- Commands needed for Lab 2. Please note that you do not have to
    // implement them for the first submission. ---

    @Override
    public String authenticate(String username) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }


    interface Callback {
        void onResponse(String response);
    }
}
