package nameserver;

/**
 * Created by Mathias on 04/01/2017.
 */
public class NameserverEntry {
    private INameserver nameserver;
    private INameserverForChatserver nameserverForChatserver;

    public NameserverEntry(INameserver nameserver, INameserverForChatserver nameserverForChatserver){
        this.nameserver=nameserver;
        this.nameserverForChatserver=nameserverForChatserver;
    }

    public INameserver getNameserver() {
        return nameserver;
    }

    public void setNamerserver(final INameserver namerserver) {
        this.nameserver = namerserver;
    }

    public INameserverForChatserver getNameserverForChatserver() {
        return nameserverForChatserver;
    }

    public void setNameserverForChatserver(final INameserverForChatserver nameserverForChatserver) {
        this.nameserverForChatserver = nameserverForChatserver;
    }
}
