package au.csiro.jsprinkler;

import static org.junit.Assert.assertEquals;

import org.junit.Ignore;
import org.junit.Test;

public class Tester {

    private static final String ENDPOINT =
//            "http://localhost:8080/ontoserver/resources/fhir";
//            "http://ontoserver.csiro.au/fhir";
            "http://52.4.97.158:8080/ontoserver/resources/fhir";
    private static final String SCRIPT = "/Users/law223/Downloads/tx_test_script.xml";

    @Test
    @Ignore("For speed, don't bother testing main API")
    public void testMain() throws Exception {
        final String[] args = {
                ENDPOINT,
                SCRIPT
        };

        TestScript.main(args);
    }

    @Test
    public void testScript() throws Exception {
        TestScript script = new TestScript(ENDPOINT);
        script.run(SCRIPT);

        assertEquals(55, script.getTotal());
//        assertEquals(0, script.getFail());
    }

}
