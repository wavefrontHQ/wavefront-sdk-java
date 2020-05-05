package com.wavefront.sdk.entities.tracing.sampling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

/**
 * @author Anil Kodali (akodali@vmware.com).
 */
public class MinConditionalSamplerTest {

  private static final String SPAN_OP_NAME = "spanOperationName";
  private static final long SPAN_MIN_DURATION = 10;
  MinConditionalSampler minConditionalSampler = new MinConditionalSampler(new ArrayList<>(),
      SPAN_MIN_DURATION);

  /**
   * Test valid span i.e. span more than minimum duration gets accepted.
   */
  @Test
  public void testValidSpan() {
    assertTrue(minConditionalSampler.sample(SPAN_OP_NAME, 1231, 13),
        "Span duration is greater than minimum threshold and expected to be sampled.");

  }

  /**
   * Test Invalid span i.e. span less than minimum duration gets discarded.
   */
  @Test
  public void testInvalidSpan() {
    assertFalse(minConditionalSampler.sample(SPAN_OP_NAME, 1231, 8),
        "Span duration is less than minimum threshold and expected to be discarded.");
  }

}
