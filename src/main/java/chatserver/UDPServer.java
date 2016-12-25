package chatserver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UDPServer implements Runnable {

    private static int threadCounter = 0;

    private int port;

    private DatagramSocket socket;

    private Thread thread;

    public UDPServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        thread = Thread.currentThread();
        ExecutorService executor = Executors.newCachedThreadPool();
        try (DatagramSocket socket = new DatagramSocket(port)){
            this.socket = socket;
            while(!Thread.interrupted()){
                byte[] buf = new byte[256];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                executor.execute(new UPDWorker(packet));
            }
            executor.shutdown();
        } catch (IOException e) {
            if(!e.getMessage().equals("Socket closed")){
                e.printStackTrace();
            }
        }
    }

    public void shutdown(){
        if(socket != null){
            socket.close();
        }
        if(thread != null){
            thread.interrupt();
        }
    }

    class UPDWorker implements Runnable {

        private DatagramPacket packet;

        public UPDWorker(DatagramPacket packet){
            this.packet = packet;
        }

        @Override
        public void run() {
            Thread.currentThread().setName("UDPWorker #" + ++threadCounter);
            String data = new String(packet.getData(), 0, packet.getLength());
            if(data.equals("list")){
                Collection<User> users = UserStore.getInstance().listUsers();
                StringBuilder sb = new StringBuilder();
                for(User user : users){
                    if(user.isOnline()){
                        sb.append(user.getName()).append("\r\n");
                    }
                }
                byte[] payload = sb.toString().getBytes();
                DatagramPacket response = new DatagramPacket(payload, payload.length, packet.getAddress(), packet.getPort());

                try {
                    socket.send(response);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

}
