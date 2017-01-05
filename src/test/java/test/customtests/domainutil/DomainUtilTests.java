package test.customtests.domainutil;

import nameserver.DomainUtil;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Created by mkamleithner on 05.01.17.
 */
public class DomainUtilTests {


    @Test
    public void testSplit(){

        String domain = "bob.berlin.de";

        List<String> splitUp = DomainUtil.splitDomainIntoParts(domain);

        assertEquals(3, splitUp.size() );

        assertEquals("bob", splitUp.get(0));
        assertEquals("berlin", splitUp.get(1));
        assertEquals("de", splitUp.get(2));

    }

    @Test
    public void testJoin(){

        String domain = "bob.berlin.de";

        List<String> splitUp = DomainUtil.splitDomainIntoParts(domain);

        String joined = DomainUtil.joinPartsToDomain(splitUp);

        assertEquals(domain, joined);

    }


    @Test
    public void testUsernameMatch(){

        String domain = "berlin.de";

        String wrongDomain = "de";

        String wrongDomain2= "berlin.at";

        String username = "bob.berlin.de";

        assertTrue(DomainUtil.userNameMatchesDomain(username, domain));
        assertFalse(DomainUtil.userNameMatchesDomain(username, wrongDomain));
        assertFalse(DomainUtil.userNameMatchesDomain(username, wrongDomain2));


    }

    @Test
    public void testGetUsernameFromQualifiedName(){

        String username = "bob.berlin.de";

        assertEquals("bob", DomainUtil.getUsernameFromFullyQualifiedName(username));

    }

}
