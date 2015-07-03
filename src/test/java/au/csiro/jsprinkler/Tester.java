package au.csiro.jsprinkler;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Ignore;
import org.junit.Test;

public class Tester {

    private static final String[] ENDPOINT = {
//        "http://localhost:8080/ontoserver/resources/fhir",
        "http://ontoserver.csiro.au/fhir",
        "http://fhir-dev.healthintersections.com.au/open",
//        "http://fhirplace.health-samurai.io/",
//        "http://fhir.nortal.com/fhir-server",
//        "https://fhir-open-api-dstu2.smarthealthit.org",
//        "http://spark-dstu2.furore.com/fhir",
//        "http://nprogram.azurewebsites.net",
//        "http://fhir-dev.healthconnex.com.au/fhir",
//        "http://52.4.97.158:8080/ontoserver/resources/fhir",
    };

    private static final String SCRIPT = "/Users/law223/Downloads/tx_test_script.xml";

    @Test
    @Ignore("For speed, don't bother testing main API")
    public void testMain() throws Exception {
        final String[] args = {
                ENDPOINT[0],
                SCRIPT
        };

        TestScript.main(args);
    }

    @Test
    public void testScript() throws Exception {
        final int nTests = 55;
        final List<Integer> failed = new ArrayList<>();

        for (String server: ENDPOINT) {
            System.out.println("Endpoint: " + server);
            TestScript script = new TestScript(server);
            script.run(SCRIPT);

            assertEquals(nTests, script.getTotal());

//            for (String f: script.getFailedTests()) {
//                System.err.println(f);
//            }

            failed.add(script.getFail());

//            assertEquals(0, script.getFail());
        }

        System.out.println("\nPassed\tFailed\tEndpoint");
        for (int i = 0; i < ENDPOINT.length; i++) {
            System.out.println((nTests - failed.get(i)) + "\t" + failed.get(i) + "\t" + ENDPOINT[i]);
        }
    }

}
