/**
 * Copyright 2015 CSIRO Australian e-Health Research Centre (http://aehrc.com).
 *
 * All rights reserved. Use is subject to license terms and conditions.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package au.csiro.jsprinkler;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.time.StopWatch;
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

    private List<String> fail = new ArrayList<>();

    private Namespace ns;

    private boolean doSetup = true;

    private String currentTest;

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
        return run(new FileReader(testFile));
    }

    public int run(Reader testFile) throws JDOMException, IOException {
        final Document doc = new SAXBuilder().build(testFile);

        final Element root = doc.getRootElement();
        if (!"TestScript".equals(root.getName()) || !"http://hl7.org/fhir".equals(root.getNamespaceURI())) {
            throw new IllegalArgumentException("Unrecognised start to script: expected TestScript :: http://hl7.org/fhir");
        }

        ns = root.getNamespace();

        System.out.println("Running Test Script: " + getChildValue(root, "name"));

        final StopWatch sw = new StopWatch();
        sw.start();

        for (Element child: root.getChildren()) {
            if (doSetup && "setup".equals(child.getName())) {
                executeSetup(child);
            }
            if ("suite".equals(child.getName())) {
                executeSuite(child);
            }
        }
        sw.stop();

        System.out.println("Finish Test Script. Elapsed Time = " + sw);
        System.out.println("Tests: " + total +"\tPassed: " + (total-fail.size()) +"\tFailed: " + fail.size());

        return fail.size();
    }

    public int getTotal() {
        return total;
    }

    public int getFail() {
        return fail.size();
    }

    public List<String> getFailedTests() {
        return fail;
    }

    public boolean isDoSetup() {
        return doSetup;
    }

    public void setDoSetup(boolean doSetup) {
        this.doSetup = doSetup;
    }


    private void executeSuite(Element element) {
        println("Execute Suite " + getChildValue(element, "name"));
        for (Element child: element.getChildren()) {
            final String name = child.getName();

            if ("test".equals(name)) {
                executeTest(child);
            } else if (!"name".equals(name) && !"description".equals(name)) {
                throw new IllegalArgumentException("Unexpected content: " + name);
            }
        }
    }

    private void executeTest(Element element) {
        final String id = element.getAttributeValue("id");
        if (testId == null || testId.equals(id)) {
            currentTest = getChildValue(element, "name") + (id == null ? "" : " ["+id+"]");
            print("  Test " + currentTest + ": ");
            total++;

            for (Element child: element.getChildren()) {
                final String name = child.getName();
                if ("operation".equals(name)) {
                    executeOperation(child);
                } else if (!"name".equals(name) && !"description".equals(name)) {
                    throw new IllegalArgumentException("Unexpected content: " + name);
                }
            }

        }
    }

    private void executeOperation(Element element) {
        final StopWatch sw = new StopWatch();
        sw.start();

        try {
            final String url = getChildValue(element, "url");
            final Element input = element.getChild("input", ns);

            Resource result;
            int actualResponse = -1;

            try {
                final ResourceRequest<Resource> request;
                if (input != null) {
                    final Parameters paramList = (Parameters) getInnerResource(input);

                    final byte[] bytes = new XmlParser().composeBytes(paramList);
                    final URI uri = URI.create(endpoint+"/"+url);

                    request = ClientUtils.issuePostRequest(uri, bytes, client.getPreferredResourceFormat(), null);
                } else {
                    final URI uri = URI.create(endpoint+"/"+url);

                    request = ClientUtils.issueGetResourceRequest(uri, client.getPreferredResourceFormat(), null);
                }
                actualResponse = request.getHttpStatus();
                result = request.getPayload();
            } catch (EFhirClientException e) {
                if (e.hasServerErrors() && e.getServerErrors().size() == 1) {
                    result = e.getServerErrors().get(0);
                } else {
                    throw e;
                }
            }
            sw.stop();

            checkResult(element, result, actualResponse, sw);
        } catch (Exception e) {
            if (!sw.isStopped()) {
                sw.stop();
            }
            fail.add(currentTest);
            println("Failed (" + sw + ")\n    [" +e.getClass().getSimpleName()+"] "+ e.getMessage());
        }
    }

    protected boolean checkRange(String range, int code) {
        if (range.startsWith("!")) {
            return !checkRange(range.substring(1), code);
        } else if (range.endsWith("xx")) {
            int lower = Integer.parseInt(range.substring(0, 1)) * 100;
            int upper = lower + 100;
            return lower <= code && code < upper;
        } else {
            return code == Integer.parseInt(range);
        }
    }

    protected void checkResult(Element element, final Resource result, int responseStatus, StopWatch sw) {
        final String expectedResponse = getChildValue(element, "responseCode");

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
            if (checkRange(expectedResponse, responseStatus)) {
                println("Passed (" + sw + ")");
                return;
            } else {
                comp.getErrors().add(0, "Response code mismatch - expected " + expectedResponse + " got " + responseStatus);
            }
        }

        fail.add(currentTest);
        println("Failed (" + sw + ")");
        for (String err: comp.getErrors()) {
            println("    "+err);
        }
    }

    private void executeSetup(Element element) {
        println("Setup");
        for (Element child: element.getChildren()) {
            if ("action".equals(child.getName())) {
                final StopWatch sw = new StopWatch();
                sw.start();
                print(" " + getChildValue(child, "name"));
                try {
                    executeSetupAction(child);
                } catch (EFhirClientException e) {
                    print(" Failed " + e.getMessage());
                }
                sw.stop();
                println(" (" + sw + ")");
            }
        }
    }

    private void executeSetupAction(Element element) {
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
            final String xmlString = new XMLOutputter().outputString(element);
            return new XmlParser().parse(xmlString);
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

    private void print(final String line) {
        System.out.print(line);
    }

    private void println(final String line) {
        System.out.println(line);
    }

}
