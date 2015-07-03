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

import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.instance.model.Base;
import org.hl7.fhir.instance.model.BooleanType;
import org.hl7.fhir.instance.model.Extension;
import org.hl7.fhir.instance.model.OperationOutcome;
import org.hl7.fhir.instance.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.instance.model.Property;
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
                errors.add("Found a Resource of type " + actual.getResourceType() +" ["+actual.getId()+"]" + ", but expected none.");
                return false;
            }
        }
        if (null == actual) {
            errors.add("Expected a Resource of type " + expected.getResourceType() +" ["+expected.getId()+"]" + ", but found none.");
            return false;
        }

        if (!actual.getResourceType().equals(expected.getResourceType())) {
            String detail = "";
            if (actual instanceof OperationOutcome) {
                try {
                    final OperationOutcome outcome = (OperationOutcome) actual;
                    for (OperationOutcomeIssueComponent issue: outcome.getIssue()) {
                        detail = ": " + detail + issue.getDetails();
                    }
//                    new NarrativeGenerator("", new WorkerContext()).generate(outcome);
//                    detail = ": " + outcome.getText().getDiv().getContent();
                } catch (Exception e) {
                }
            }
            errors.add("Found a Resource of type " + actual.getResourceType() + ", but expected " + expected.getResourceType() + detail);
            return false;
        }

        if ("min".equals(rule)) {
            return compareElementsMin(expected.getResourceType().toString(), expected, actual, true);
        } else {
            throw new RuntimeException("Unknown rule: " + rule);
        }
    }

    public List<String> getErrors() {
        return errors;
    }

    private boolean compareElementsMin(String path, Resource expected, Resource actual, boolean reportErrors) {
        // given that it's "min", then our task is
        //  * iterate the expected
        //  * anything in that, find it in the observed
        //  * check the value for fixed value or specified pattern
        //  * check for list management extensions

        return compareBase(path, expected, actual);
    }

    protected boolean compareBase(String path, Base expected, Base actual) {
        boolean ok = true;

        if (null == expected) {
            if (null == actual) {
                return true;
            } else if (actual instanceof BooleanType && !((BooleanType) actual).getValue()) {
                // no value equivalent to false
                return true;
            } else {
                errors.add(path + " - Found [" + actual + "], but expected none.");
                return false;
            }
        }
        if (null == actual) {
            if (expected instanceof BooleanType && !((BooleanType) expected).getValue()) {
                // no value equivalent to false
                return true;
            } else {
                errors.add(path + " - Expected [" + expected + "], but found none.");
                return false;
            }
        }

        for (Property ep: expected.children()) {
            if (!"Extension".equals(ep.getName())) {
                List<Base> expectedValues = ep.getValues();
                List<Base> actualValues = actual.getChildByName(ep.getName()).getValues();

                compareBaseObjects(path, expectedValues, actualValues);
            }
        }

//        ok = Resource.compareDeep(expected, actual, true);
//
//        if (!ok) {
//            List<Property> ec = expected.children();
//            List<Property> ac = actual.children();
//            ok = compareProperties(path, ec, ac);
//        }

        return ok;
    }

    protected <T extends Base> boolean compareBaseObjects(String name, List<T> expected, List<T> actual) {
        boolean ok = true, suffix = false;
        int minExpected = expected.size();
        for (T o: expected) {
            if (o instanceof Extension) {
                minExpected--;
            }
        }
        if (minExpected > actual.size()) {
            errors.add(name + " - Differing numbers of children: expected " + minExpected + ", found " + actual.size());

            ok = false;
            suffix = true;
        }

        int min = Math.min(minExpected, actual.size());
        for (int i = 0; i < min; i++) {
            ok &= compareBase(name+(suffix?"["+i+"]":""), expected.get(i), actual.get(i));
        }

        return ok;
    }

    protected boolean compareProperties(String name, List<Property> expected, List<Property> actual) {
        boolean ok = true, suffix = false;
        if (expected.size() != actual.size()) {
            errors.add("Differing numbers of children: expected " + expected.size() + ", found " + actual.size());
            ok = false;
            suffix = true;
        }

        int min = Math.min(expected.size(), actual.size());
        for (int i = 0; i < min; i++) {
            ok &= compareProperty(name+(suffix?"["+i+"]":""), expected.get(i), actual.get(i));
        }

        return ok;
    }

    protected boolean  compareProperty(String name, Property expected, Property actual) {
        if (!expected.getName().equals(actual.getName())) {
            errors.add("Property names differ: expected " + expected.getName() + ", found " + actual.getName());
            return false;
        } else {
            return compareBaseObjects(name+"::"+expected.getName(), expected.getValues(), actual.getValues());
        }
    }

}
