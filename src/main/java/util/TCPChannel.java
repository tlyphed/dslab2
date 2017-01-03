package util;

import java.io.*;
import java.net.Socket;

public class TCPChannel implements IChannel{

    private String host;
    private Integer port;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;

    public TCPChannel(String host, Integer port){
        if(host == null || port == null){
            throw new IllegalArgumentException();
        }
        this.host = host;
        this.port = port;
    }

    public void open(){
        try {

            this.socket = new Socket(host, port);
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() throws IOException {
        if(socket != null && !socket.isClosed()){
            socket.close();
        }
    }

    public void write(String msg) throws IOException {
        out.write(msg);
        out.newLine();
        out.flush();
    }

    public String read() throws IOException {
        return in.readLine();
    }
}
