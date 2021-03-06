package org.batfish.datamodel;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

/** OSPF intra-area route. Must stay within a single OSPF area. */
@ParametersAreNonnullByDefault
public class OspfIntraAreaRoute extends OspfInternalRoute {

  private static final long serialVersionUID = 1L;

  @JsonCreator
  private static OspfIntraAreaRoute jsonCreator(
      @Nullable @JsonProperty(PROP_NETWORK) Prefix network,
      @Nullable @JsonProperty(PROP_NEXT_HOP_IP) Ip nextHopIp,
      @Nullable @JsonProperty(PROP_ADMINISTRATIVE_COST) Integer admin,
      @Nullable @JsonProperty(PROP_METRIC) Long metric,
      @Nullable @JsonProperty(PROP_AREA) Long area) {
    checkArgument(network != null, "%s must be specified", PROP_NETWORK);
    checkArgument(nextHopIp != null, "%s must be specified", PROP_NEXT_HOP_IP);
    checkArgument(admin != null, "%s must be specified", PROP_ADMINISTRATIVE_COST);
    checkArgument(metric != null, "%s must be specified", PROP_METRIC);
    checkArgument(area != null, "%s must be specified", PROP_AREA);
    return new OspfIntraAreaRoute(network, nextHopIp, admin, metric, area, false, false);
  }

  public OspfIntraAreaRoute(
      Prefix network,
      Ip nextHopIp,
      int admin,
      long metric,
      long area,
      boolean nonForwarding,
      boolean nonRouting) {
    super(network, nextHopIp, admin, metric, area, nonForwarding, nonRouting);
  }

  @Nonnull
  @Override
  public RoutingProtocol getProtocol() {
    return RoutingProtocol.OSPF;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    } else if (!(o instanceof OspfIntraAreaRoute)) {
      return false;
    }
    OspfIntraAreaRoute other = (OspfIntraAreaRoute) o;
    return Objects.equals(_network, other._network)
        && _admin == other._admin
        && _area == other._area
        && getNonRouting() == other.getNonRouting()
        && getNonForwarding() == other.getNonForwarding()
        && Objects.equals(_metric, other._metric)
        && Objects.equals(_nextHopIp, other._nextHopIp);
  }

  @Override
  public int hashCode() {
    return Objects.hash(_network, _admin, _area, _metric, _nextHopIp);
  }

  @Override
  public int routeCompare(AbstractRoute rhs) {
    if (getClass() != rhs.getClass()) {
      return 0;
    }
    OspfIntraAreaRoute castRhs = (OspfIntraAreaRoute) rhs;
    return Long.compare(_area, castRhs._area);
  }

  @Override
  public Builder toBuilder() {
    return builder()
        // AbstractRoute properties
        .setNetwork(getNetwork())
        .setNextHopIp(getNextHopIp())
        .setAdmin(getAdministrativeCost())
        .setMetric(getMetric())
        .setNonForwarding(getNonForwarding())
        .setNonRouting(getNonRouting())
        // OspfIntraAreaRoute properties
        .setArea(getArea());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder extends AbstractRouteBuilder<Builder, OspfIntraAreaRoute> {

    private Long _area;

    @Override
    public OspfIntraAreaRoute build() {
      return new OspfIntraAreaRoute(
          getNetwork(),
          getNextHopIp(),
          getAdmin(),
          getMetric(),
          _area,
          getNonForwarding(),
          getNonRouting());
    }

    @Nonnull
    @Override
    protected Builder getThis() {
      return this;
    }

    public Builder setArea(long area) {
      _area = area;
      return this;
    }

    private Builder() {}
  }
}
