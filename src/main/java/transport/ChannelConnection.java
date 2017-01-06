package transport;

import java.io.IOException;
import java.util.concurrent.Semaphore;

public class ChannelConnection implements Runnable {

    private Thread readingThread;

    public interface ResponseListener {
        void onResponse(String response);
    }

    private IChannel channel;

    private String lastResponse;

    private final Semaphore available = new Semaphore(1, true);

    private boolean silentMode = false;

    private ResponseListener responseListener;

    public ChannelConnection(IChannel channel) {
        this.channel = channel;
        this.readingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("ReadingThread");
                try {
                    processInput();
                } catch (IOException | InterruptedException e) {
                    if (!(e.getMessage().equals("Stream closed") || e.getMessage().equals("Socket closed"))) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @Override
    public void run() {
        try {
            available.acquire();

            open();

            readingThread.start();

            readingThread.join();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
        }
    }

    protected void open() throws IOException{
        channel.open();
        startReadingThread();
    }

    public void close() throws IOException {
        available.release();
        channel.close();
    }

    private void processInput() throws IOException, InterruptedException {
        String line;
        while (!Thread.currentThread().isInterrupted()) {
            available.acquire();
            if ((line = channel.read()) != null) {
                lastResponse = line;
                if (!silentMode) {
                    fireResponseEvent(line);
                }
            } else {
                break;
            }
            available.release();
        }
    }

    protected void fireResponseEvent(String msg) {
        if (responseListener != null) {
            responseListener.onResponse(msg);
        }
    }

    public void setResponseListener(ResponseListener responseListener) {
        this.responseListener = responseListener;
    }

    public void writeToServer(String msg) throws IOException {
        channel.write(msg);
    }

    public void writeToServer(String msg, final boolean silent, final ResponseListener callback) throws IOException {
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

    public void startReadingThread() {
        available.release();
    }
}
