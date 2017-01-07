package chatserver;

import cli.Command;
import cli.Shell;
import nameserver.INameserver;
import nameserver.INameserverForChatserver;
import util.Config;
import util.IPublicKeyStore;
import util.Keys;
import util.PublicKeyStore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.security.PrivateKey;

public class Chatserver implements IChatserverCli, Runnable {

    private String componentName;
    private Config config;
    private InputStream userRequestStream;
    private PrintStream userResponseStream;

    private UDPServer udpServer;
    private TCPServer tcpServer;

    private Shell shell;

    /**
     * @param componentName      the name of the component - represented in the prompt
     * @param config             the configuration to use
     * @param userRequestStream  the input stream to read user input from
     * @param userResponseStream the output stream to write the console output to
     */
    public Chatserver(String componentName, Config config, InputStream userRequestStream, PrintStream userResponseStream) {
        this.componentName = componentName;
        this.config = config;
        this.userRequestStream = userRequestStream;
        this.userResponseStream = userResponseStream;

        UserStore.getInstance().loadFromFile();

        shell = new Shell(componentName, userRequestStream, userResponseStream);

        shell.register(this);
    }

    @Override
    public void run() {
        Thread.currentThread().setName("ChatserverThread");

        try {

            Registry registry = LocateRegistry.getRegistry(
                    config.getString("registry.host"),
                    config.getInt("registry.port"));
            // look for the bound server remote-object implementing the IServer
            // interface
            INameserverForChatserver root = (INameserver) registry.lookup(config
                    .getString("root_id"));

            IPublicKeyStore keyStore = new PublicKeyStore(new File(config.getString("keys.dir")));
            PrivateKey chatserverKey = Keys.readPrivatePEM(new File(config.getString("key")));

            tcpServer = new TCPServer(config.getInt("tcp.port"), keyStore, chatserverKey, root);
            udpServer = new UDPServer(config.getInt("udp.port"));

            Thread udpServerThread = new Thread(udpServer);
            udpServerThread.setName("UPDServerThread");
            udpServerThread.start();

            Thread tcpServerThread = new Thread(tcpServer);
            tcpServerThread.setName("TCPServerThread");
            tcpServerThread.start();

            Thread shellThread = new Thread(shell);
            shellThread.setName("ShellThread");
            shellThread.start();

            try {
                udpServerThread.join();
                tcpServerThread.join();
                shellThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } catch (NotBoundException | RemoteException e) {
            userResponseStream.println("could not find root server, exiting...");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    @Command
    public String users() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (User user : UserStore.getInstance().listUsers()) {
            sb.append(user.getName() + " " + (user.isOnline() ? "online" : "offline"));
            sb.append("\r\n");
        }

        return sb.toString();
    }

    @Override
    @Command
    public String exit() throws IOException {
        udpServer.shutdown();
        tcpServer.shutdown();

        shell.close();
        userRequestStream.close();
        userResponseStream.close();

        return "Shutdown complete.";
    }

    /**
     * @param args the first argument is the name of the {@link Chatserver}
     *             component
     */
    public static void main(String[] args){
        String name = "chatserver";
        if (args.length > 0) {
            name = args[0];
        }
        Chatserver chatserver = new Chatserver(name, new Config("chatserver"), System.in, System.out);
        (new Thread(chatserver)).start();
    }

}
