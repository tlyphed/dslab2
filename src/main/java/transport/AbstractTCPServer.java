package transport;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class AbstractTCPServer implements Runnable {

    private static int threadCounter = 0;

    private ConcurrentLinkedQueue<TCPWorker> workers = new ConcurrentLinkedQueue<>();

    private int port;

    private ServerSocket serverSocket;

    private Thread thread;

    public AbstractTCPServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        thread = Thread.currentThread();
        ExecutorService executor = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            this.serverSocket = serverSocket;
            while (!Thread.interrupted()) {
                Socket socket = serverSocket.accept();
                TCPWorker tcpWorker = new TCPWorker(wrapSocket(socket));
                workers.add(tcpWorker);
                executor.execute(tcpWorker);
            }
        } catch (IOException e) {
            if (!e.getMessage().equals("Socket closed")) {
                e.printStackTrace();
            }
        }
        executor.shutdown();
    }

    protected ConcurrentLinkedQueue<TCPWorker> getWorkers() {
        return workers;
    }

    public void shutdown() throws IOException {
        for (TCPWorker worker : workers) {
            worker.shutdown();
        }
        if (thread != null) {
            thread.interrupt();
        }
        if (serverSocket != null) {
            serverSocket.close();
        }

    }

    protected IChannel wrapSocket(Socket socket){
        return new TCPChannel(socket);
    }

    protected abstract void processInput(TCPWorker worker, IChannel channel) throws IOException;

    protected class TCPWorker implements Runnable {

        private IChannel channel;
        private Thread thread;

        TCPWorker(IChannel channel) {
            this.channel = channel;
        }

        public void shutdown() throws IOException {
            channel.close();
            if (thread != null) {
                thread.interrupt();
            }
        }

        public IChannel getChannel() {
            return channel;
        }

        @Override
        public void run() {
            thread = Thread.currentThread();
            Thread.currentThread().setName("TCPWorker #" + ++threadCounter);

            channel.open();

            Thread readingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Thread.currentThread().setName("TCP-ReadingThread");
                    try {
                        processInput(TCPWorker.this, channel);
                    } catch (IOException e) {
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
            workers.remove(this);

        }
    }

    public static boolean checkPort(int port) {
        try (ServerSocket s = new ServerSocket(port)) {

        } catch (IOException e) {
            return false;
        }
        return true;
    }


}
