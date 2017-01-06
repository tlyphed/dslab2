package transport;

import java.io.IOException;

public interface IChannel {

    void open() throws IOException;

    void close() throws IOException;

    void write(String msg) throws IOException;

    String read() throws IOException;

}
