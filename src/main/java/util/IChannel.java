package util;

import java.io.IOException;

public interface IChannel {

    void open();

    void close() throws IOException;

    void write(String msg) throws IOException;

    String read() throws IOException;

}
