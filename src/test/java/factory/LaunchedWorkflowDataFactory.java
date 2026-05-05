package factory;

import model.LaunchedWorkflow;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class LaunchedWorkflowDataFactory {

    private static Properties props = new Properties();

    static {
        try (InputStream is = LaunchedWorkflowDataFactory.class
                .getClassLoader()
                .getResourceAsStream("config.properties")) {

            props.load(is);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load workflow properties", e);
        }
    }

    public static LaunchedWorkflow createWorkflow(String identityName, String taskName) {

        LaunchedWorkflow wf = new LaunchedWorkflow();

        // workflow name from properties
        wf.workflowName = props.getProperty("workflow.name");
        
     // build input object

        // --- Input 1: identityName ---
        LaunchedWorkflow.Input input1 = new LaunchedWorkflow.Input();
        input1.key = "identityName";
        input1.value = identityName;

        // --- Input 2: provisioning plan ---
        LaunchedWorkflow.Input input2 = new LaunchedWorkflow.Input();
        input2.key = "taskName";
        input2.value = taskName;

        // multi-valued list
        wf.input = List.of(input1, input2);
        
        return wf;
    }
}