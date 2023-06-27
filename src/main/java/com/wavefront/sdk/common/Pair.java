package com.wavefront.sdk.common;

/**
 * Pair class to easily package a 2 dimensional tuple
 *
 * @author Sushant Dewan (sushant@wavefront.com).
 * @version $Id: $Id
 */
public class Pair<T, V> {
  public final T _1;
  public final V _2;

  /**
   * <p>Constructor for Pair.</p>
   *
   * @param t a T object
   * @param v a V object
   */
  public Pair(T t, V v) {
    this._1 = t;
    this._2 = v;
  }

  /** {@inheritDoc} */
  @Override
  public int hashCode() {
    return _1.hashCode() + 43 * _2.hashCode();
  }

  /** {@inheritDoc} */
  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Pair) {
      Pair pair = (Pair) obj;
      return _1.equals(pair._1) && _2.equals(pair._2);
    }
    return false;
  }

  /** {@inheritDoc} */
  @Override
  public String toString() {
    return "com.wavefront.sdk.common.Pair (" + this._1.toString() + ", " + this._2.toString() + ")";
  }

  /**
   * <p>of.</p>
   *
   * @param t a T object
   * @param v a V object
   * @param <T> a T class
   * @param <V> a V class
   * @return a {@link com.wavefront.sdk.common.Pair} object
   */
  public static <T, V> Pair<T, V> of(T t, V v) {
    return new Pair<T, V>(t, v);
  }
}
