package transport;

import java.io.IOException;
import java.util.concurrent.Semaphore;

public class ChannelConnection implements Runnable{

    private Thread readingThread;

    public interface ResponseListener {
        void onResponse(String response);
    }

    private IChannel channel;

    private String lastResponse;

    private boolean listening = false;

    private final Semaphore available = new Semaphore(1, true);

    private boolean silentMode = false;

    private ResponseListener responseListener;

    public ChannelConnection(IChannel channel){
        this.channel = channel;
    }

    @Override
    public void run(){
        try {
            channel.open();

            readingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Thread.currentThread().setName("ReadingThread");
                    try {
                        if(listening){
                            processInput();
                        }
                    } catch (IOException | InterruptedException e) {
                        if (!(e.getMessage().equals("Stream closed") || e.getMessage().equals("Socket closed"))) {
                            e.printStackTrace();
                        }
                    }
                }
            });

            readingThread.start();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void close() throws IOException {
        channel.close();
        try {
            readingThread.join();
        } catch (InterruptedException e) {
        }
    }

    private void processInput() throws IOException, InterruptedException {
        String line;
        while (!Thread.currentThread().isInterrupted()) {
            available.acquire();
            if ((line = channel.read()) != null) {
                lastResponse = line;
                if(!silentMode){
                    fireResponseEvent(line);
                }
            } else {
                break;
            }
            available.release();
        }
    }

    private void fireResponseEvent(String msg){
        if(responseListener != null){
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

    public void setListening(boolean listening) {
        this.listening = listening;
    }

    public boolean isListening() {
        return listening;
    }
}
