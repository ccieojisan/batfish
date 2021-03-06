package org.batfish.representation.cumulus;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import org.batfish.datamodel.RoutingProtocol;

public enum CumulusRoutingProtocol {
  CONNECTED,
  STATIC;

  /**
   * Maps each {@link CumulusRoutingProtocol} to the set of {@link RoutingProtocol}s it includes.
   */
  public static final Map<CumulusRoutingProtocol, Set<RoutingProtocol>> VI_PROTOCOLS_MAP =
      ImmutableMap.of(
          CONNECTED, ImmutableSet.of(RoutingProtocol.CONNECTED),
          STATIC, ImmutableSet.of(RoutingProtocol.STATIC));
}
