package nameserver;

import chatserver.User;
import cli.Command;
import cli.Shell;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import util.Config;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Please note that this class is not needed for Lab 1, but will later be used
 * in Lab 2. Hence, you do not have to implement it for the first submission.
 */
public class Nameserver implements INameserverCli, INameserver, Runnable {

    private String componentName;
    private Config config;
    private InputStream userRequestStream;
    private PrintStream userResponseStream;
    private INameserver rootNS;
    private Shell shell;
    private ConcurrentMap<String, NameserverEntry> nameServers;
    private ConcurrentSkipListMap<String, User> users;

    private String mDomain;
    private boolean isRoot;

    /**
     * @param componentName      the name of the component - represented in the prompt
     * @param config             the configuration to use
     * @param userRequestStream  the input stream to read user input from
     * @param userResponseStream the output stream to write the console output to
     */
    public Nameserver(String componentName, Config config,
                      InputStream userRequestStream, PrintStream userResponseStream) {
        this.componentName = componentName;
        this.config = config;
        this.userRequestStream = userRequestStream;
        this.userResponseStream = userResponseStream;
        nameServers = new ConcurrentSkipListMap<>();

        users = new ConcurrentSkipListMap<>();

        //Register Shell
        shell = new Shell(componentName, userRequestStream, userResponseStream);
        shell.register(this);
    }

    @Override
    public void run() {

        //Start Shellthread
        Thread shellThread = new Thread(shell);
        shellThread.setName("ShellThread");
        shellThread.start();

        this.isRoot = checkRoot(config);

        // TODO
        //If the config does not contain "domain" this Nameserver is the root-ns
        if (isRoot) {
            this.mDomain = ".";
            try {
                // create and export the registry instance on localhost at the
                // specified port
                Registry registry = LocateRegistry.createRegistry(config
                        .getInt("registry.port"));

                // create a remote object of this server object
                INameserver remote = (INameserver) UnicastRemoteObject
                        .exportObject(this, 0);
                // bind the obtained remote object on specified binding name in the
                // registry
                registry.bind(config.getString("root_id"), remote);
            } catch (RemoteException e) {
                throw new RuntimeException("Error while starting server.", e);
            } catch (AlreadyBoundException e) {
                throw new RuntimeException(
                        "Error while binding remote object to registry.", e);
            }
        } else {
            this.mDomain = config.getString("domain");
            //Regular Nameserver (not root-ns)
            try {
                // obtain registry that was created by the server
                rootNS = lookupRoot(config);

                rootNS.registerNameserver(config.getString("domain"), (INameserver) UnicastRemoteObject
                        .exportObject(this, 0), this);

            } catch (RemoteException e) {
                userResponseStream.println("Could not connect to registry, please start the root nameserver");
            } catch (NotBoundException e) {
                userResponseStream.println("Fatal: could not bind!" + e.getMessage());
            } catch (AlreadyRegisteredException e) {
                userResponseStream.println("The domain of this Nameserver is already registered");
            } catch (InvalidDomainException e) {
                userResponseStream.println("The domain of this Nameserver is invalid");
            }
        }
    }

    private INameserver lookupRoot(Config config) throws RemoteException, NotBoundException {

        Registry registry = LocateRegistry.getRegistry(
                config.getString("registry.host"),
                config.getInt("registry.port"));
        // look for the bound server remote-object implementing the IServer
        // interface
        return (INameserver) registry.lookup(config
                .getString("root_id"));

    }

    private boolean checkRoot(Config config) {
        return !config.listKeys().contains("domain");
    }

    @Override
    @Command
    public String nameservers() throws IOException {
        //nameservers is a sorted map
        int i = 1;
        for (String k : nameServers.keySet()) {
            userResponseStream.println(i + ". " + k);
            i++;
        }
        return null;
    }

    @Override
    @Command
    public String addresses() throws IOException {
        // TODO Auto-generated method stub
        Set<Map.Entry<String, User>> userSet = this.users.entrySet();
        List<String> lines = new ArrayList<>(userSet.size());
        int i = 1;
        for (Map.Entry<String, User> user : userSet) {
            lines.add(String.format("%d. %s %s", i++, DomainUtil.getUsernameFromFullyQualifiedName(user.getKey()), user.getValue().getIpAddress()));
        }

        StringBuilder addresses = new StringBuilder();
        for (String line : lines) {
            addresses.append(line)
                    .append(System.lineSeparator());
        }
        return addresses.toString();


    }

    @Override
    @Command
    public String exit() throws IOException {

        // TODO Auto-generated method stub
        return null;
    }

    /**
     * @param args the first argument is the name of the {@link Nameserver}
     *             component
     */
    public static void main(String[] args) {
        Nameserver nameserver = new Nameserver(args[0], new Config(args[0]),
                System.in, System.out);
        nameserver.run();
        // TODO: start the nameserver
    }

    @Override
    public void registerNameserver(final String domain, final INameserver nameserver, final INameserverForChatserver nameserverForChatserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

        List<String> domains = DomainUtil.splitDomainIntoParts(domain);
        userResponseStream.println("Registering domain " + domain);
        if (domains.size() == 1) {
            userResponseStream.println("Registered domain " + domain);
            //Register domain by saving the RMI objects in local storage
            if(nameServers.putIfAbsent(domain, new NameserverEntry(nameserver, nameserverForChatserver)) != null){
                throw new AlreadyRegisteredException(domain);
            }
        } else if (domains.size() > 1) {
            userResponseStream.println("Passing register request to " + domains.get(domains.size() - 1));

            //Pass the request on to the next domain level
            INameserver topDomain = nameServers.get(domains.get(domains.size() - 1)).getNameserver();
            domains.remove(domains.size() - 1);
            topDomain.registerNameserver(rebuildDomainFromList(domains), nameserver, nameserverForChatserver);
            //Todo: throw exception if request can't be passed on
        }

    }

    @Override
    public void registerUser(final String username, final String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

        if (username == null) {
            throw new InvalidDomainException(username);
        }

        List<String> userDomainPArts = DomainUtil.splitDomainIntoParts(username);
        if (userDomainPArts.size() != 1) {
            registerUserRecursively(userDomainPArts, address);
        } else {
            registerUserLocally(username, address);
        }

    }

    /**
     * @param userName   the user name, e.q. bob.berlin.de
     * @param address    the user address
     * @throws RemoteException
     * @throws AlreadyRegisteredException
     * @throws InvalidDomainException
     */
    private void registerUserRecursively(List<String> userName, String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {
        String nextNs = DomainUtil.lastPart(userName);
        INameserverForChatserver nextServer = this.getNameserver(nextNs);
        if (nextServer == null) {
            throw new InvalidDomainException(nextNs);
        }
        nextServer.registerUser(DomainUtil.excludeLastPart(userName), address);
    }

    private void registerUserLocally(String username, String address) throws AlreadyRegisteredException {
        User user = new User();
        user.setIpAddress(address);
        user.setName(username);
        //insert user atomically if possible, otherwise throw exception
        if (users.putIfAbsent(username, user) != null) {
            throw new AlreadyRegisteredException(username);
        }
    }

    @Override
    public INameserverForChatserver getNameserver(final String zone) throws RemoteException {
        NameserverEntry entry = this.nameServers.get(zone);
        if (entry == null) {
            userResponseStream.println("no zone  " + zone);
            return null;
        }
        userResponseStream.println("get nameserver for " + zone);
        return entry.getNameserverForChatserver();
    }

    @Override
    public String lookup(final String username) throws RemoteException {

        userResponseStream.println("lookup " + username);

        User user = this.users.get(username);
        if (user == null) {
            return null;
        }
        return user.getIpAddress();


    }

    /**
     * Rebuilds the Domain-String out of the split list
     * e.g.: {"vienna","at"} to "vienna.at"
     *
     * @param list list containing the subdomains
     * @return String of the rebuit subdomains
     */
    private String rebuildDomainFromList(List<String> list) {
        return DomainUtil.joinPartsToDomain(list);

    }
}
