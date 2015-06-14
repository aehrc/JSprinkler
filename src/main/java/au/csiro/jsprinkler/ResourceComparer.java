package au.csiro.jsprinkler;

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.Resource;

public class ResourceComparer {

    final private String rule;
    final private Resource expected;
    final private Resource actual;

    final private List<String> errors = new ArrayList<>();

    public ResourceComparer(String rule, Resource expected, Resource actual) {
        this.rule = rule;
        this.expected = expected;
        this.actual = actual;
    }

    public boolean execute() {
        if (null == expected) {
            if (null == actual) {
                return true;
            } else {
                errors.add("Found Resource " + actual.getResourceType() + " expected none.");
                return false;
            }
        }
        if (null == actual) {
            errors.add("Expectd Resource " + expected.getResourceType() + " found none.");
            return false;
        }

        if (!actual.getResourceType().equals(expected.getResourceType())) {
            errors.add("Found Resource Type " + actual.getResourceType() + " expected " + expected.getResourceType());
            return false;
        }

        if (!"min".equals(rule)) {
            throw new RuntimeException("Unknown rule: " + rule);
        }

        return compareElementsMin(expected.getResourceType().toString(), expected, actual, true);
    }

    public List<String> getErrors() {
        return errors;
    }

    private boolean compareElementsMin(String string, Resource expected2, Resource actual2, boolean reportErrors) {
        boolean ok = true;



        return ok;
    }
}
