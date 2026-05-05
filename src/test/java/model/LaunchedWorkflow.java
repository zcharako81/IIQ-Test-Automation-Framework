package model;

import java.util.List;
import java.util.Map;

public class LaunchedWorkflow {

    public String workflowName;

    public List<Input> input;

    public static class Input {

        public String key;

        public Object value;

    }
}