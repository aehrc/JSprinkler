package au.csiro.jsprinkler;

import org.junit.Test;

public class Tester {

    @Test
    public void test() throws Exception {
        final String[] args = {
                "http://localhost:8080/ontoserver/resources/fhir",
                "/Users/law223/Downloads/tx_test_script.xml"
        };

        TestScript.main(args);
    }

}
