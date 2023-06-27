package com.wavefront.sdk.entities.histograms;

/**
 * Granularity (minute, hour, or day) by which histograms distributions are aggregated.
 *
 * @author Han Zhang (zhanghan@vmware.com).
 * @version $Id: $Id
 */
public enum HistogramGranularity {
  MINUTE("!M"),
  HOUR("!H"),
  DAY("!D");

  public final String identifier;

  HistogramGranularity(String identifier) {
    this.identifier = identifier;
  }
}
