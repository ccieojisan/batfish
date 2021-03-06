package org.batfish.representation.palo_alto;

import static org.batfish.representation.palo_alto.PaloAltoConfiguration.CATCHALL_SERVICE_NAME;
import static org.batfish.representation.palo_alto.PaloAltoConfiguration.computeServiceGroupMemberAclName;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.commons.collections4.list.TreeList;
import org.batfish.datamodel.IpAccessList;
import org.batfish.datamodel.IpAccessListLine;
import org.batfish.datamodel.LineAction;
import org.batfish.datamodel.acl.MatchHeaderSpace;
import org.batfish.datamodel.acl.PermittedByAcl;
import org.batfish.datamodel.acl.TrueExpr;

public final class ServiceGroup implements ServiceGroupMember {
  private static final long serialVersionUID = 1L;

  private final String _name;

  private final SortedSet<ServiceOrServiceGroupReference> _references;

  public ServiceGroup(String name) {
    _name = name;
    _references = new TreeSet<>();
  }

  @Override
  public String getName() {
    return _name;
  }

  public SortedSet<ServiceOrServiceGroupReference> getReferences() {
    return _references;
  }

  @Override
  public IpAccessList toIpAccessList(LineAction action, PaloAltoConfiguration pc, Vsys vsys) {
    List<IpAccessListLine> lines = new TreeList<>();
    for (ServiceOrServiceGroupReference memberReference : _references) {
      // Check for matching object before using built-ins
      String vsysName = memberReference.getVsysName(pc, vsys);
      String serviceName = memberReference.getName();

      if (vsysName != null) {
        lines.add(
            new IpAccessListLine(
                action,
                new PermittedByAcl(
                    computeServiceGroupMemberAclName(vsysName, memberReference.getName())),
                _name));
      } else if (serviceName.equals(CATCHALL_SERVICE_NAME)) {
        lines.clear();
        lines.add(new IpAccessListLine(action, TrueExpr.INSTANCE, _name));
        break;
      } else if (serviceName.equals(ServiceBuiltIn.SERVICE_HTTP.getName())) {
        lines.add(
            new IpAccessListLine(
                action, new MatchHeaderSpace(ServiceBuiltIn.SERVICE_HTTP.getHeaderSpace()), _name));
      } else if (serviceName.equals(ServiceBuiltIn.SERVICE_HTTPS.getName())) {
        lines.add(
            new IpAccessListLine(
                action,
                new MatchHeaderSpace(ServiceBuiltIn.SERVICE_HTTPS.getHeaderSpace()),
                _name));
      }
    }
    return IpAccessList.builder()
        .setName(_name)
        .setLines(lines)
        .setSourceName(_name)
        .setSourceType(PaloAltoStructureType.SERVICE_GROUP.getDescription())
        .build();
  }
}
