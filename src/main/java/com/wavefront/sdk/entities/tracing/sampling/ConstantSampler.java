package com.wavefront.sdk.entities.tracing.sampling;

/**
 * Sampler that allows spans through at a constant rate (all in or all out).
 *
 * @author Vikram Raman (vikram@wavefront.com)
 */
public class ConstantSampler implements Sampler {

  private volatile boolean decision;

  public ConstantSampler(boolean decision) {
    this.decision = decision;
  }

  @Override
  public boolean sample(String operationName, long traceId, long duration) {
    return decision;
  }

  @Override
  public boolean isEarly() {
    return true;
  }

  /**
   * Sets the decision for this sampler.
   *
   * @param decision the sampling decision
   */
  public void setDecision(boolean decision) {
    this.decision = decision;
  }
}
