package org.batfish.bddreachability.transition;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;
import net.sf.javabdd.BDD;

/** A transition that applies a constraint. */
final class Constraint implements Transition {
  private final BDD _constraint;

  Constraint(BDD constraint) {
    checkArgument(!constraint.isOne(), "Cannot build Constraint with BDD 1. Use Identity instead.");
    checkArgument(!constraint.isZero(), "Cannot build Constraint with BDD 0. Use Zero instead.");
    _constraint = constraint;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Constraint)) {
      return false;
    }
    Constraint that = (Constraint) o;
    return Objects.equals(_constraint, that._constraint);
  }

  @Override
  public int hashCode() {
    return Objects.hash(_constraint);
  }

  @Override
  public BDD transitForward(BDD bdd) {
    return _constraint.and(bdd);
  }

  @Override
  public BDD transitBackward(BDD bdd) {
    return _constraint.and(bdd);
  }
}
