package com.wavefront.sdk.common.application;

import org.junit.Rule;
import org.junit.contrib.java.lang.system.EnvironmentVariables;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 *
 * @author German Laullon (glaullon@vmware.com)
 */
public class ApplicationTagsTest {

    @Rule
    public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

    @Test
    public void testCustomTags() {
        environmentVariables.set("my_var1", "var_value1");
        environmentVariables.set("my_var2", "var_value2");
        environmentVariables.set("not_my_var3", "var_value3");
        environmentVariables.set("VERSION", "1.0");

        ApplicationTags appTags = new ApplicationTags.Builder("my_app", "main")
                .tagsFromEnv("my_.*")
                .tagFromEnv("VERSION", "app_version")
                .build();

        assertEquals("my_var1", appTags.getCustomTags().get("var_value1"));
        assertEquals("my_var2", appTags.getCustomTags().get("var_value2"));
        assertEquals("not_my_var3", appTags.getCustomTags().get("var_value3"));
        assertEquals("VERSION", appTags.getCustomTags().get("1.0"));
    }
}
