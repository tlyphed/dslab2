package util;

import java.io.*;
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
                TCPWorker tcpWorker = new TCPWorker(socket);
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

    protected ConcurrentLinkedQueue<TCPWorker> getWorkers(){
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

    protected abstract void processInput(TCPWorker worker, BufferedReader in, BufferedWriter out) throws IOException;

    protected class TCPWorker implements Runnable {

        private Socket socket;
        private BufferedWriter output;
        private Thread thread;

        TCPWorker(Socket socket) {
            this.socket = socket;
        }

        public void shutdown() throws IOException {
            socket.shutdownInput();
            if (thread != null) {
                thread.interrupt();
            }
        }

        public BufferedWriter getOutput() {
            return output;
        }

        @Override
        public void run() {
            thread = Thread.currentThread();
            Thread.currentThread().setName("TCPWorker #" + ++threadCounter);
            try (final BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 final BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                 final Socket socket = this.socket) {

                output = out;

                Thread readingThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Thread.currentThread().setName("TCP-ReadingThread");
                        try {
                            processInput(TCPWorker.this, in, out);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });

                readingThread.start();
                try {
                    readingThread.join();
                } catch (InterruptedException e) {
                }
                workers.remove(this);
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public static boolean checkPort(int port){
        try (ServerSocket s = new ServerSocket(port)){

        } catch (IOException e) {
            return false;
        }
        return true;
    }


}
