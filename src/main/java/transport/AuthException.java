package transport;

import java.io.IOException;

public class AuthException extends IOException {
    public AuthException(Exception e){
        super(e);
    }
    public AuthException(String msg){
        super(msg);
    }
}
