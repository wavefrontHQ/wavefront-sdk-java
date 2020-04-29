package com.wavefront.sdk.common.application;

import java.util.HashMap;
import java.util.Map;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.partialMockBuilder;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

/**
 *
 * @author German Laullon (glaullon@vmware.com)
 */
public class ApplicationTagsTest {

    @Test
    public void testCustomTags() {
        Map<String, String> env = new HashMap<>();
        env.put("my_var1", "var_value1");
        env.put("my_var2", "var_value2");
        env.put("not_my_var3", "var_value3");
        env.put("VERSION", "1.0");

        Map<String, String> custom = new HashMap<>();
        custom.put("tag", "tag_value1");

        ApplicationTags.Builder mockBuilder = partialMockBuilder(ApplicationTags.Builder.class)
                .withConstructor("app", "ser")
                .addMockedMethod("getenv")
                .createMock();
        expect(mockBuilder.getenv()).andReturn(env).anyTimes();
        replay(mockBuilder);

        ApplicationTags appTags = mockBuilder
                .customTags(custom)
                .tagsFromEnv("my_.*")
                .tagFromEnv("VERSION", "app_version")
                .cluster("cluster")
                .build();

        assertEquals("tag_value1", appTags.getCustomTags().get("tag"));
        assertEquals("var_value1", appTags.getCustomTags().get("my_var1"));
        assertEquals("var_value2", appTags.getCustomTags().get("my_var2"));
        assertNull(appTags.getCustomTags().get("not_my_var3"));
        assertEquals("1.0", appTags.getCustomTags().get("app_version"));

        Map<String, String> pointTag = appTags.toPointTags();
        assertEquals("app", pointTag.get("application"));
        assertEquals("cluster", pointTag.get("cluster"));
        assertEquals("none", pointTag.get("shard"));
        assertEquals("var_value1", pointTag.get("my_var1"));
        assertNull(pointTag.get("not_my_var3"));
    }
}
