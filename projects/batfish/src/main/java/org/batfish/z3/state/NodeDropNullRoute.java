package org.batfish.z3.state;

import javax.annotation.ParametersAreNonnullByDefault;
import org.batfish.z3.expr.StateExpr;
import org.batfish.z3.state.visitors.StateExprVisitor;

@ParametersAreNonnullByDefault
public final class NodeDropNullRoute implements StateExpr {

  private final String _hostname;

  public NodeDropNullRoute(String hostname) {
    _hostname = hostname;
  }

  @Override
  public <R> R accept(StateExprVisitor<R> visitor) {
    return visitor.visitNodeDropNullRoute(this);
  }

  public String getHostname() {
    return _hostname;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NodeDropNullRoute)) {
      return false;
    }
    NodeDropNullRoute that = (NodeDropNullRoute) o;
    return _hostname.equals(that._hostname);
  }

  @Override
  public int hashCode() {
    return _hostname.hashCode();
  }
}
