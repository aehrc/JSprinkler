package au.csiro.jsprinkler;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.client.ClientUtils;
import org.hl7.fhir.instance.client.EFhirClientException;
import org.hl7.fhir.instance.client.FHIRSimpleClient;
import org.hl7.fhir.instance.client.ResourceRequest;
import org.hl7.fhir.instance.formats.XmlParser;
import org.hl7.fhir.instance.model.OperationOutcome;
import org.hl7.fhir.instance.model.Parameters;
import org.hl7.fhir.instance.model.Resource;
import org.hl7.fhir.instance.model.ValueSet;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestScript {

    final private static Logger log = LoggerFactory.getLogger(TestScript.class);

    private static String testId;


    final private String endpoint;

    final private FHIRSimpleClient client;

    private int total = 0;

    private int fail = 0;

    private Namespace ns;

    private boolean doSetup = true;

    public static void main(String[] args) throws Exception {
        final List<String> testFiles = new ArrayList<>();
        final String endpoint = processArgs(testFiles, args);

        for (String testFile: testFiles) {
            TestScript script = new TestScript(endpoint);
            script.run(testFile);
        }
    }

    private static String processArgs(List<String> files, String[] args) {
        String endpoint = null;

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("-")) {
                if ("-testId".equals(a)) {
                    i++;
                    testId = args[i];
                }
            } else {
                endpoint = a;
                i++;
                while (i < args.length) {
                    files.add(args[i++]);
                }
            }
        }

        return endpoint;
    }

    public TestScript(String endpoint) throws URISyntaxException {
        this.endpoint = endpoint;
        this.client = new FHIRSimpleClient();

        client.initialize(endpoint);
    }

    public int run(String testFile) throws JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder();
        Document doc = builder.build(new File(testFile));

        Element root = doc.getRootElement();
        if (!"TestScript".equals(root.getName()) || !"http://hl7.org/fhir".equals(root.getNamespaceURI())) {
            throw new IllegalArgumentException("Unrecognised start to script: expected TestScript :: http://hl7.org/fhir");
        }

        ns = root.getNamespace();

        System.out.println("Running: " + getChildValue(root, "name"));

        for (Element child: root.getChildren()) {
            if (doSetup && "setup".equals(child.getName())) {
                executeSetup(child);
            }
            if ("suite".equals(child.getName())) {
                executeSuite(child);
            }
        }

        System.out.println("Tests: " + total +" Failures: " + fail);

        return fail;
    }

    public int getTotal() {
        return total;
    }

    public int getFail() {
        return fail;
    }

    public boolean isDoSetup() {
        return doSetup;
    }

    public void setDoSetup(boolean doSetup) {
        this.doSetup = doSetup;
    }


    private void executeSuite(Element element) {
        System.out.println(getChildValue(element, "name"));
        for (Element child: element.getChildren()) {
            if ("test".equals(child.getName())) {
                executeTest(child);
            }
        }
    }

    private void executeTest(Element element) {
        final String id = element.getAttributeValue("id");
        if (testId == null || testId.equals(id)) {
            System.out.println("Test " + getChildValue(element, "name") + (id == null ? "" : "["+id+"]"));
            total++;

            for (Element child: element.getChildren()) {
                if ("operation".equals(child.getName())) {
                    executeOperation(child);
                }
            }

        }
    }

    private void executeOperation(Element element) {
        try {
            final String url = getChildValue(element, "url");
            final Element input = element.getChild("input", ns);

            final ResourceRequest<Resource> request;

            if (input != null) {
                final Parameters paramList = (Parameters) getInnerResource(input);
                final String name = getChildValue(element, "name");

                final byte[] bytes = new XmlParser().composeBytes(paramList);
                final URI uri = URI.create(endpoint+"/"+url+ (name == null ? "" : "$"+name));

                System.err.println(uri);

                request = ClientUtils.issuePostRequest(uri, bytes, client.getPreferredResourceFormat(), null);

            } else {
                final URI uri = URI.create(endpoint+"/"+url);

                request = ClientUtils.issueGetResourceRequest(uri, client.getPreferredResourceFormat(), null);
                request.addErrorStatus(410);        // gone
                request.addErrorStatus(404);        // unknown
                request.addSuccessStatus(200);      // ok
            }

            if (request.isUnsuccessfulRequest()) {
                throw new EFhirClientException("Server returned error code " + request.getHttpStatus(), (OperationOutcome) request.getPayload());
            }

            final Resource result = request.getPayload();

            Element output = element.getChild("output", ns);
            Element rules = null;
            for (Element child: output.getChildren()) {
                if (rules == null && "rules".equals(child.getName())) {
                    rules = child;
                } else if (rules != null) {
                    output = child;
                    break;
                }
            }
            final Resource expected = getOuterResource(output);

            final ResourceComparer comp = new ResourceComparer(rules.getAttributeValue("value"), expected, result);
            if (comp.execute()) {
                System.out.println("Passed");
            } else {
                fail++;
                System.out.println("Failed");
                for (String err: comp.getErrors()) {
                    System.err.println("\t"+err);
                }
            }
        } catch (EFhirClientException e) {
            fail++;
            System.out.println("Failed: " + e.getMessage());
        } catch (URISyntaxException e) {
            fail++;
            System.out.println("Failed: " + e.getMessage());
        } catch (Exception e) {
            fail++;
            System.out.println("Failed: " + e.getMessage());
        }
    }

    private void executeSetup(Element element) {
        for (Element child: element.getChildren()) {
            if ("action".equals(child.getName())) {
                executeSetupAction(child);
            }
        }
    }

    private void executeSetupAction(Element element) {
        System.out.println(getChildValue(element, "name"));
        for (Element child: element.getChildren()) {
            if ("update".equals(child.getName())) {
                executeUpdate(child);
            }
        }
    }

    private void executeUpdate(Element element) {
        final Resource resource = getInnerResource(element);

        final Class<Resource> resourceClass = (Class<Resource>) resource.getClass();
        final String id = resource.getId();

        Resource result = client.update(resourceClass, resource, id);

        handleResult(result);
    }


    private static Resource getOuterResource(Element element) {
        try {
            return new XmlParser().parse(new XMLOutputter().outputString(element));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    protected static Resource getInnerResource(Element element) {
        final Element inner = element.getChildren().get(0);
        return getOuterResource(inner);
    }

    protected static void handleResult(Resource result) {
        if (result instanceof OperationOutcome) {
            final OperationOutcome outcome = (OperationOutcome) result;
//            System.err.println(result.getResourceType() + ":: " + outcome.getText().getDiv().allText());
//            for (OperationOutcomeIssueComponent issue: outcome.getIssue()) {
//                System.err.println("    " + issue.getSeverity() + " / " + issue.getCode() + ":: " + issue.getDetails());
//            }
        } else if (result instanceof ValueSet) {
            final ValueSet valueset = (ValueSet) result;
//            System.err.println(result.getResourceType() + ":: " + valueset.getText().getDiv().allText());
        } else {
//            System.err.println(result.getResourceType() + ":: " + result.getChildByName("name"));
        }
    }

    private Element getChild(Element parent, String name) {
        return parent.getChild(name, ns);
    }

    private String getChildValue(Element parent, String name) {
        final Element child = parent
                .getChild(name, ns);
        return child == null ? "" : child.getAttributeValue("value");
    }

}
