package nameserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import cli.Shell;
import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import util.Config;

/**
 * Please note that this class is not needed for Lab 1, but will later be used
 * in Lab 2. Hence, you do not have to implement it for the first submission.
 */
public class Nameserver implements INameserverCli, INameserver,INameserverForChatserver, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	private Registry registry;
	private INameserver rootNS;
	private Shell shell;

	/**
	 * @param componentName
	 *            the name of the component - represented in the prompt
	 * @param config
	 *            the configuration to use
	 * @param userRequestStream
	 *            the input stream to read user input from
	 * @param userResponseStream
	 *            the output stream to write the console output to
	 */
	public Nameserver(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

		shell = new Shell(componentName, userRequestStream, userResponseStream);

		shell.register(this);

		//If the config does not contain "domain" this Nameserver is the root-ns
		if(!config.listKeys().contains("domain")){
			try {
				// create and export the registry instance on localhost at the
				// specified port
				registry = LocateRegistry.createRegistry(config
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
		}else{
			//Regular Nameserver (not root-ns)
			try {
				// obtain registry that was created by the server
				registry = LocateRegistry.getRegistry(
						config.getString("registry.host"),
						config.getInt("registry.port"));
				// look for the bound server remote-object implementing the IServer
				// interface
				rootNS = (INameserver) registry.lookup(config
						.getString("root_id"));

			} catch (RemoteException e) {
				throw new RuntimeException(
						"Error while obtaining registry/server-remote-object.", e);
			} catch (NotBoundException e) {
				throw new RuntimeException(
						"Error while looking for server-remote-object.", e);
			}
		}

		// TODO
	}

	@Override
	public void run() {
		// TODO
	}

	@Override
	public String nameservers() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String addresses() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String exit() throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Nameserver}
	 *            component
	 */
	public static void main(String[] args) {
		Nameserver nameserver = new Nameserver(args[0], new Config(args[0]),
				System.in, System.out);
		// TODO: start the nameserver
	}

	@Override
	public void registerNameserver(final String domain, final INameserver nameserver, final INameserverForChatserver nameserverForChatserver) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

	}

	@Override
	public void registerUser(final String username, final String address) throws RemoteException, AlreadyRegisteredException, InvalidDomainException {

	}

	@Override
	public INameserverForChatserver getNameserver(final String zone) throws RemoteException {
		return null;
	}

	@Override
	public String lookup(final String username) throws RemoteException {
		return "serverresponse";
	}
}
