package org.batfish.datamodel.answers;

import static org.batfish.datamodel.BgpSessionProperties.SessionType.EBGP_MULTIHOP;
import static org.batfish.datamodel.BgpSessionProperties.SessionType.EBGP_SINGLEHOP;
import static org.batfish.datamodel.BgpSessionProperties.SessionType.IBGP;
import static org.batfish.datamodel.FlowDisposition.DELIVERED_TO_SUBNET;
import static org.batfish.datamodel.FlowDisposition.EXITS_NETWORK;
import static org.batfish.datamodel.FlowDisposition.INSUFFICIENT_INFO;
import static org.batfish.datamodel.FlowDisposition.NEIGHBOR_UNREACHABLE_OR_EXITS_NETWORK;
import static org.batfish.datamodel.FlowState.ESTABLISHED;
import static org.batfish.datamodel.FlowState.NEW;
import static org.batfish.datamodel.FlowState.RELATED;
import static org.batfish.datamodel.Protocol.HTTP;
import static org.batfish.datamodel.Protocol.HTTPS;
import static org.batfish.datamodel.Protocol.SSH;
import static org.batfish.datamodel.answers.AutoCompleteUtils.stringAutoComplete;
import static org.batfish.datamodel.questions.BgpPeerPropertySpecifier.IS_PASSIVE;
import static org.batfish.datamodel.questions.BgpPeerPropertySpecifier.LOCAL_AS;
import static org.batfish.datamodel.questions.BgpPeerPropertySpecifier.REMOTE_AS;
import static org.batfish.datamodel.questions.BgpProcessPropertySpecifier.MULTIPATH_EBGP;
import static org.batfish.datamodel.questions.BgpProcessPropertySpecifier.MULTIPATH_EQUIVALENT_AS_PATH_MATCH_MODE;
import static org.batfish.datamodel.questions.BgpProcessPropertySpecifier.MULTIPATH_IBGP;
import static org.batfish.datamodel.questions.ConfiguredSessionStatus.DYNAMIC_MATCH;
import static org.batfish.datamodel.questions.ConfiguredSessionStatus.NO_MATCH_FOUND;
import static org.batfish.datamodel.questions.ConfiguredSessionStatus.UNIQUE_MATCH;
import static org.batfish.datamodel.questions.InterfacePropertySpecifier.ACCESS_VLAN;
import static org.batfish.datamodel.questions.InterfacePropertySpecifier.ALLOWED_VLANS;
import static org.batfish.datamodel.questions.InterfacePropertySpecifier.AUTO_STATE_VLAN;
import static org.batfish.datamodel.questions.InterfacePropertySpecifier.ENCAPSULATION_VLAN;
import static org.batfish.datamodel.questions.InterfacePropertySpecifier.NATIVE_VLAN;
import static org.batfish.datamodel.questions.IpsecSessionStatus.IKE_PHASE1_FAILED;
import static org.batfish.datamodel.questions.IpsecSessionStatus.IKE_PHASE1_KEY_MISMATCH;
import static org.batfish.datamodel.questions.IpsecSessionStatus.IPSEC_PHASE2_FAILED;
import static org.batfish.datamodel.questions.NamedStructureSpecifier.AS_PATH_ACCESS_LIST;
import static org.batfish.datamodel.questions.NamedStructureSpecifier.IP_6_ACCESS_LIST;
import static org.batfish.datamodel.questions.NamedStructureSpecifier.IP_ACCESS_LIST;
import static org.batfish.datamodel.questions.NodePropertySpecifier.DNS_SERVERS;
import static org.batfish.datamodel.questions.NodePropertySpecifier.DNS_SOURCE_INTERFACE;
import static org.batfish.datamodel.questions.OspfPropertySpecifier.AREAS;
import static org.batfish.datamodel.questions.OspfPropertySpecifier.AREA_BORDER_ROUTER;
import static org.batfish.specifier.DispositionSpecifier.SUCCESS;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.batfish.common.CompletionMetadata;
import org.batfish.datamodel.collections.NodeInterfacePair;
import org.batfish.datamodel.questions.NodePropertySpecifier;
import org.batfish.datamodel.questions.Variable.Type;
import org.batfish.referencelibrary.AddressGroup;
import org.batfish.referencelibrary.InterfaceGroup;
import org.batfish.referencelibrary.ReferenceBook;
import org.batfish.referencelibrary.ReferenceLibrary;
import org.batfish.role.NodeRole;
import org.batfish.role.NodeRoleDimension;
import org.batfish.role.NodeRolesData;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class AutoCompleteUtilsTest {

  @Rule public ExpectedException _thrown = ExpectedException.none();

  private static Set<String> getSuggestionsTextSet(List<AutocompleteSuggestion> suggestions) {
    return suggestions.stream()
        .map(AutocompleteSuggestion::getText)
        .collect(ImmutableSet.toImmutableSet());
  }

  private static CompletionMetadata getMockCompletionMetadata() {
    return CompletionMetadata.builder()
        .setNodes(
            ImmutableSet.of(
                "host1", "host2", "router1", "spine", "leaf", "\"/foo/leaf\"", "enternet1"))
        .setInterfaces(
            ImmutableSet.of(
                new NodeInterfacePair("host1", "interface1"),
                new NodeInterfacePair("host1", "ethernet1"),
                new NodeInterfacePair("host2", "ethernet1"),
                new NodeInterfacePair("host2", "gigEthernet1"),
                new NodeInterfacePair("router1", "eth1"),
                new NodeInterfacePair("router1", "ge0"),
                new NodeInterfacePair("spine", "int1"),
                new NodeInterfacePair("leaf", "leafInterface"),
                new NodeInterfacePair("\"/foo/leaf\"", "fooInterface")))
        .setIps(ImmutableSet.of("1.1.1.1", "11.2.3.4", "3.1.2.4", "1.2.3.4", "4.4.4.4"))
        .setVrfs(ImmutableSet.of("default"))
        .build();
  }

  @Test
  public void testBaseAutoComplete() {
    Set<String> properties =
        ImmutableSet.of(
            "abc", NodePropertySpecifier.NTP_SERVERS, NodePropertySpecifier.NTP_SOURCE_INTERFACE);

    // null or empty string should yield all options
    assertThat(
        AutoCompleteUtils.baseAutoComplete(null, properties).stream()
            .map(s -> s.getText())
            .collect(Collectors.toList()),
        equalTo(ImmutableList.builder().addAll(properties).build()));

    // the capital P shouldn't matter and this should autoComplete to two entries
    assertThat(
        new ArrayList<>(AutoCompleteUtils.baseAutoComplete("ntP", properties)),
        equalTo(
            ImmutableList.of(
                new AutocompleteSuggestion(NodePropertySpecifier.NTP_SERVERS, false),
                new AutocompleteSuggestion(NodePropertySpecifier.NTP_SOURCE_INTERFACE, false))));
  }

  @Test
  public void testAddressGroupAutocomplete() throws IOException {
    ReferenceLibrary library =
        new ReferenceLibrary(
            ImmutableList.of(
                ReferenceBook.builder("b1")
                    .setAddressGroups(
                        ImmutableList.of(
                            new AddressGroup(null, "g1"), new AddressGroup(null, "a1")))
                    .build(),
                ReferenceBook.builder("b2")
                    .setAddressGroups(ImmutableList.of(new AddressGroup(null, "g2")))
                    .build()));

    // empty matches all possibilities
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.ADDRESS_GROUP_NAME, "", 5, null, null, library)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of("g1", "a1", "g2")));

    // 'g' matches two groups
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.ADDRESS_GROUP_NAME, "G", 5, null, null, library)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of("g1", "g2")));

    // 'g1' matches one group
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.ADDRESS_GROUP_NAME, "g1", 5, null, null, library)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of("g1")));
  }

  @Test
  public void testBgpPeerPropertySpecAutocomplete() throws IOException {
    assertThat(
        AutoCompleteUtils.autoComplete(Type.BGP_PEER_PROPERTY_SPEC, "as", 5).stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of(LOCAL_AS, IS_PASSIVE, REMOTE_AS)));
  }

  @Test
  public void testBgpProcessPropertySpecAutocomplete() throws IOException {
    assertThat(
        AutoCompleteUtils.autoComplete(Type.BGP_PROCESS_PROPERTY_SPEC, "multi", 5).stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(
            ImmutableSet.of(
                MULTIPATH_EQUIVALENT_AS_PATH_MATCH_MODE, MULTIPATH_EBGP, MULTIPATH_IBGP)));
  }

  @Test
  public void testBgpSessionStatusAutocomplete() throws IOException {
    assertThat(
        AutoCompleteUtils.autoComplete(Type.BGP_SESSION_STATUS, "match", 5).stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(
            ImmutableSet.of(
                DYNAMIC_MATCH.toString(), NO_MATCH_FOUND.toString(), UNIQUE_MATCH.toString())));
  }

  @Test
  public void testBgpSessionTypeAutocomplete() throws IOException {
    assertThat(
        AutoCompleteUtils.autoComplete(Type.BGP_SESSION_TYPE, "bgp", 5).stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(
            ImmutableSet.of(IBGP.toString(), EBGP_SINGLEHOP.toString(), EBGP_MULTIHOP.toString())));
  }

  @Test
  public void testDispositionSpecAutocomplete() throws IOException {
    assertThat(
        AutoCompleteUtils.autoComplete(Type.DISPOSITION_SPEC, "s", 5).stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(
            ImmutableSet.of(
                SUCCESS,
                NEIGHBOR_UNREACHABLE_OR_EXITS_NETWORK.name().toLowerCase(),
                INSUFFICIENT_INFO.name().toLowerCase(),
                DELIVERED_TO_SUBNET.name().toLowerCase(),
                EXITS_NETWORK.name().toLowerCase())));
  }

  @Test
  public void testFilterAutocomplete() throws IOException {
    String network = "network";
    String snapshot = "snapshot";

    String suggestion = "someFilter";
    String notSuggestion = "blah";

    CompletionMetadata completionMetadata =
        CompletionMetadata.builder()
            .setFilterNames(ImmutableSet.of(suggestion, notSuggestion))
            .build();

    assertThat(
        AutoCompleteUtils.autoComplete(
                network, snapshot, Type.FILTER, "fil", 5, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of(suggestion)));
  }

  @Test
  public void testFlowStateAutocomplete() throws IOException {
    assertThat(
        AutoCompleteUtils.autoComplete(Type.FLOW_STATE, "e", 5).stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of(ESTABLISHED.toString(), RELATED.toString(), NEW.toString())));
  }

  @Test
  public void testInterfaceAutocomplete() throws IOException {
    String network = "network";
    String snapshot = "snapshot";

    NodeInterfacePair suggested = new NodeInterfacePair("hostname", "interface");
    NodeInterfacePair notSuggested = new NodeInterfacePair("blah", "blahhh");

    CompletionMetadata completionMetadata =
        CompletionMetadata.builder()
            .setInterfaces(ImmutableSet.of(suggested, notSuggested))
            .build();

    assertThat(
        AutoCompleteUtils.autoComplete(
                network, snapshot, Type.INTERFACE, "int", 5, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of(suggested.toString())));
  }

  @Test
  public void testInterfaceGroupAutocomplete() throws IOException {
    ReferenceLibrary library =
        new ReferenceLibrary(
            ImmutableList.of(
                ReferenceBook.builder("b1")
                    .setInterfaceGroups(
                        ImmutableList.of(
                            new InterfaceGroup(ImmutableSortedSet.of(), "g1"),
                            new InterfaceGroup(ImmutableSortedSet.of(), "a1")))
                    .build(),
                ReferenceBook.builder("b2")
                    .setInterfaceGroups(
                        ImmutableList.of(new InterfaceGroup(ImmutableSortedSet.of(), "g2")))
                    .build()));

    // empty matches all possibilities
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.INTERFACE_GROUP_NAME, "", 5, null, null, library)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of("g1", "a1", "g2")));

    // 'g' matches two groups
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.INTERFACE_GROUP_NAME, "G", 5, null, null, library)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of("g1", "g2")));

    // 'g1' matches one group
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.INTERFACE_GROUP_NAME, "g1", 5, null, null, library)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of("g1")));
  }

  @Test
  public void testInterfacePropertySpecAutocomplete() throws IOException {
    assertThat(
        AutoCompleteUtils.autoComplete(Type.INTERFACE_PROPERTY_SPEC, "vlan", 5).stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(
            ImmutableSet.of(
                ACCESS_VLAN, ALLOWED_VLANS, AUTO_STATE_VLAN, ENCAPSULATION_VLAN, NATIVE_VLAN)));
  }

  @Test
  public void testIpAutocomplete() throws IOException {
    String network = "network";
    String snapshot = "snapshot";

    CompletionMetadata completionMetadata =
        CompletionMetadata.builder()
            .setIps(ImmutableSet.of("1.2.3.4", "1.3.2.4", "1.23.4.5"))
            .build();

    assertThat(
        AutoCompleteUtils.autoComplete(
                network, snapshot, Type.IP, "1.2", 5, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of("1.2.3.4", "1.23.4.5")));
  }

  @Test
  public void testIpProtocolSpecAutocomplete() throws IOException {
    assertThat(
        AutoCompleteUtils.autoComplete(Type.IP_PROTOCOL_SPEC, "OSP", 5).stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of("OSPF")));
  }

  @Test
  public void testIpsecSessionStatusAutocomplete() throws IOException {
    assertThat(
        AutoCompleteUtils.autoComplete(Type.IPSEC_SESSION_STATUS, "phase", 5).stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(
            ImmutableSet.of(
                IKE_PHASE1_FAILED.toString(),
                IKE_PHASE1_KEY_MISMATCH.toString(),
                IPSEC_PHASE2_FAILED.toString())));
  }

  @Test
  public void testNamedStructureSpecAutocomplete() throws IOException {
    assertThat(
        AutoCompleteUtils.autoComplete(Type.NAMED_STRUCTURE_SPEC, "access", 5).stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of(AS_PATH_ACCESS_LIST, IP_ACCESS_LIST, IP_6_ACCESS_LIST)));
  }

  @Test
  public void testNodePropertySpecAutocomplete() throws IOException {
    assertThat(
        AutoCompleteUtils.autoComplete(Type.NODE_PROPERTY_SPEC, "dns", 5).stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of(DNS_SERVERS, DNS_SOURCE_INTERFACE)));
  }

  @Ignore
  @Test
  public void testNodeNameAutocompleteEmptyString() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // All node names in alphabetical order with escaped names at the end
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.NODE_NAME, "", 10, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(
            ImmutableList.of(
                "enternet1", "host1", "host2", "leaf", "router1", "spine", "\"/foo/leaf\"")));
  }

  @Ignore
  @Test
  public void testNodeNameAutocompleteNonPrefixCharacter() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // All node names containing ‘o’ in alphabetical order with escaped names at the end
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.NODE_NAME, "o", 10, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("host1", "host2", "router1", "\"/foo/leaf\"")));
  }

  @Ignore
  @Test
  public void testNodeNameAutocompleteOnePrefixCharacter() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // “Spine” suggested first because the input string is a prefix of it; “host1” and “host2” come
    // after alphabetically because they contain the input string
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.NODE_NAME, "s", 10, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("spine", "host1", "host2")));
  }

  @Ignore
  @Test
  public void testNodeNameAutocompletePrefixQuery() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // Node names where the input string is a prefix suggested first, then node names containing the
    // input
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.NODE_NAME, "lea", 10, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("leaf", "\"/foo/leaf\"")));
  }

  @Ignore
  @Test
  public void testNodeNameAutocompleteUnmatchableCharacterAtEnd() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // “leax” does not match any of the node names so removing characters from the end until there
    // are suggestions would give us the same suggestions as “lea”
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.NODE_NAME, "leax", 10, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("leaf", "\"/foo/leaf\"")));
  }

  @Ignore
  @Test
  public void testNodeNameAutocompleteUnmatchableCharacter() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // None of the nodenames contain “x” so removing characters from the end of the input string
    // until there are suggestions would give us the same suggestions as the empty string
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.NODE_NAME, "x", 10, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(
            ImmutableList.of(
                "enternet1", "host1", "host2", "leaf", "router1", "spine", "\"/foo/leaf\"")));
  }

  @Ignore
  @Test
  public void testNodeNameAutocompleteUnmatchableCharacterInMiddle() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // ‘Leaxf’ does not result in any suggestions so removing characters off the end until there are
    // completions gives us the same suggestions as for ‘lea’
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.NODE_NAME, "leaxf", 10, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("leaf", "\"/foo/leaf\"")));
  }

  @Ignore
  @Test
  public void testNodeNameAutocompleteEscapedPartial() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // Only one node name contains “/leaf” (escaped)
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.NODE_NAME,
                "\"/leaf\"",
                10,
                completionMetadata,
                null,
                null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("\"/foo/leaf\"")));
  }

  @Ignore
  @Test
  public void testNodeNameAutocompleteUnescapedPartial() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // Unescaped input doesn’t match anything, adding quotes gives us one match
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.NODE_NAME, "/foo", 10, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("\"/foo/leaf\"")));
  }

  @Ignore
  @Test
  public void testNodeNameAutocompleteValidInputIncluded() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // Since the input text ‘leaf’ is valid it is included as the first suggestion
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.NODE_NAME, "leaf", 10, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("leaf", "\"/foo/leaf\"")));
  }

  @Ignore
  @Test
  public void testAlreadySpecifiedSuggestionsFilteredOut() throws IOException {
    List<AutocompleteSuggestion> suggestions =
        AutoCompleteUtils.autoComplete(Type.ROUTING_PROTOCOL_SPEC, "EIGRP-INT, EIGRP-", 10);

    // should only suggest "EIGRP-EXT" because "EIGRP-INT" is already specified
    assertThat(suggestions.size(), equalTo(1));
    assertThat(suggestions.get(0).getText(), equalTo("EIGRP-EXT"));
    assertThat(suggestions.get(0).getInsertionIndex(), equalTo(11));
  }

  @Ignore
  @Test
  public void testRoutingProtocolSpecifiedMultipleTimes() throws IOException {
    List<AutocompleteSuggestion> suggestions =
        AutoCompleteUtils.autoComplete(Type.ROUTING_PROTOCOL_SPEC, "EIGRP-INT, EIGRP-INT", 10);

    // Since ‘EIGRP-INT’ has already been entered there are no valid completions here so removing
    // characters from the end gives us the same suggestions as for ‘EIGRP-INT, EIGRP-’
    assertThat(suggestions.size(), equalTo(1));
    assertThat(suggestions.get(0).getText(), equalTo("EIGRP-EXT"));
    assertThat(suggestions.get(0).getInsertionIndex(), equalTo(11));
  }

  @Ignore
  @Test
  public void testIpSpaceSpecEmptyString() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // Any Ips first in ascending order, then any node names in alphabetical order, then any partial
    // suggestions in alphabetical order
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.IP_SPACE_SPEC,
                "",
                25,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(
            ImmutableList.of(
                "1.1.1.1",
                "1.2.3.4",
                "3.1.2.4",
                "4.4.4.4",
                "11.2.3.4",
                "enternet1",
                "host1",
                "host2",
                "leaf",
                "router1",
                "spine",
                "\"/foo/leaf\"",
                "@connectedTo(",
                "@deviceType(",
                "@enter(",
                "@interfaceType(",
                "@vrf")));
  }

  @Ignore
  @Test
  public void testIpSpeceSpecSingleDigitQuery() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // first ip addresses that begin with 1 in ascending order followed by those that contain a 1,
    // then any node names containing a “1” in alphabetical order
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.IP_SPACE_SPEC,
                "1",
                15,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(
            ImmutableList.of(
                "1.1.1.1", "1.2.3.4", "11.2.3.4", "3.1.2.4", "enternet1", "host1", "router1")));
  }

  @Ignore
  @Test
  public void testIpSpaceSpecSingleDigitAndPeriod() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // First Ip addresses that begin with ‘1.’, then addresses that contain the value 1 (as in
    // 00000001) as one of it’s octets, then addresses that contain a ‘1.’
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.IP_SPACE_SPEC,
                "1.",
                15,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("1.1.1.1", "1.2.3.4", "3.1.2.4", "11.2.3.4")));
  }

  @Ignore
  @Test
  public void testIpSpaceSpecPartialIpQuery() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // First any Ip addresses that begin with ‘1.2’, then addresses that contain the value 1 as one
    // of it’s octets followed by a ‘.2’, then addresses that contain a ‘1.2’
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.IP_SPACE_SPEC,
                "1.2",
                15,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("1.2.3.4", "3.1.2.4", "11.2.3.4")));
  }

  @Ignore
  @Test
  public void testIpSpaceSpecValidIpQuery() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // Exact match first, then any addresses that contain ‘1.2.3.4’, then any operators that we can
    // provide completions for, then operators that we can’t provide completions for (we stop giving
    // suggestions if the user enters ‘:’ for an ip wildcard)
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.IP_SPACE_SPEC,
                "1.2.3.4",
                15,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("1.2.3.4", "11.2.3.4", "-", "/", "\\", "&", ",", ":")));
  }

  @Ignore
  @Test
  public void testIpSpaceSpecIpRange() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // Only Ip addresses that are greater than the address at the start of the range in ascending
    // order
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.IP_SPACE_SPEC,
                "1.2.3.4-",
                15,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("3.1.2.4", "4.4.4.4", "11.2.3.4")));
  }

  @Ignore
  @Test
  public void testIpSpaceSpecIpRangeNoEndRange() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // None of the addresses in the network are less than 11.2.3.4 so those shouldn’t be suggested
    // to end the range. Removing characters from the end gives us the same suggestions that would
    // be returned for ‘11.2.3.4’
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.IP_SPACE_SPEC,
                "11.2.3.4-",
                15,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("11.2.3.4", "/", "\\", "&", ",", ":")));
  }

  @Ignore
  @Test
  public void testIpSpaceSpecSingleLetterCharacter() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // First any node names that begin with ‘e’, then partial suggestions that begin with ‘e’
    // (disregarding @), then any node names that contain ‘e’, then partial suggestions that contain
    // ‘e’
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.IP_SPACE_SPEC,
                "e",
                15,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(
            ImmutableList.of(
                "enternet1",
                "@enter(",
                "leaf",
                "router1",
                "spine",
                "\"/foo/leaf\"",
                "@connectedTo(",
                "@deviceType(",
                "@interfaceType(")));
  }

  @Ignore
  @Test
  public void testIpSpaceSpecFunctionsOnly() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // Just the valid function names in alphabetical order
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.IP_SPACE_SPEC,
                "@",
                15,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(
            ImmutableList.of(
                "@connectedTo(", "@deviceType(", "@enter(", "@interfaceType(", "@vrf")));
  }

  @Ignore
  @Test
  public void testInterfacesSpecEmptyString() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // Alphabetical with interfaces first and then nodes, then functions with valid completions,
    // then operators
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.INTERFACES_SPEC,
                "",
                25,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(
            ImmutableList.of(
                "ethernet1",
                "eth1",
                "fooInterface",
                "ge0",
                "gigEthernet1",
                "interface1",
                "int1",
                "leafInterface",
                "enternet1",
                "host1",
                "host2",
                "leaf",
                "router1",
                "spine",
                "\"/foo/leaf\"",
                "@connectedTo(",
                "@deviceType(",
                "@interfaceType(",
                "@vrf(",
                "/",
                "\"",
                "(")));
  }

  @Ignore
  @Test
  public void testInterfacesSpecSingleCharacter() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // First interfaces beginning with 'e', then nodes beginning with 'e', then interfaces
    // containing 'e', then nodes containing 'e', then functions containing 'e'
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.INTERFACES_SPEC,
                "e",
                25,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(
            ImmutableList.of(
                "ethernet1",
                "eth1",
                "enternet1",
                "fooInterface",
                "ge0",
                "gigEthernet1",
                "interface1",
                "leafInterface",
                "leaf",
                "router1",
                "spine",
                "\"/foo/leaf\"",
                "@connectedTo(",
                "@deviceType(",
                "@interfaceType(")));
  }

  @Ignore
  @Test
  public void testInterfacesSpecNodeNamePrefix() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // “Router1” first because the input text is a prefix, then interfaces, nodes, functions
    // containing 'r'
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.INTERFACES_SPEC,
                "r",
                25,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(
            ImmutableList.of(
                "router1",
                "ethernet1",
                "fooInterface",
                "gigEthernet1",
                "interface1",
                "leafInterface",
                "enternet1",
                "@interfaceType(",
                "@vrf(")));
  }

  @Ignore
  @Test
  public void testInterfacesSpecExactMatch() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // Exact match first, then suggestions where input string is a prefix, then any remaining
    // interfaces/nodes containing the input string, and finally any partial suggestions
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.INTERFACES_SPEC,
                "leaf",
                25,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("leaf", "leafInterface", "\"/foo/leaf\"", "[", ",", "&", "\\")));
  }

  @Ignore
  @Test
  public void testInterfacesSpecInvalid() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // ‘leaxf’ does not result in any suggestions so removing characters off the end until there are
    // completions gives us the same suggestions as for ‘lea’: first interfaces that begin with
    // 'lea', then nodes that begin with 'lea', then interfaces, nodes that contain 'lea'
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.INTERFACES_SPEC,
                "leaxf",
                25,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("leafInterface", "leaf", "\"/foo/leaf\"")));
  }

  @Ignore
  @Test
  public void testInterfacesSpecInterfaceWithNode() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // First any partial suggestions: first interfaces on the node, then functions, then operators
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.INTERFACES_SPEC,
                "leaf[",
                25,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(
            ImmutableList.of(
                "leafInterface", "@connectedTo(", "@interfaceType(", "@vrf(", "/", "(", "\"")));
  }

  @Ignore
  @Test
  public void testInterfacesSpecValidInterface() throws IOException {
    CompletionMetadata completionMetadata = getMockCompletionMetadata();

    // First any suggestions that would make a valid input, then any partial suggestions
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network",
                "snapshot",
                Type.INTERFACES_SPEC,
                "leaf[leafInterface",
                25,
                completionMetadata,
                NodeRolesData.builder().build(),
                new ReferenceLibrary(ImmutableList.of()))
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(ImmutableList.toImmutableList()),
        equalTo(ImmutableList.of("]", "\\", "&", ",")));
  }

  @Test
  public void testNodeRoleDimensionAutocomplete() throws IOException {
    String network = "network";

    NodeRoleDimension suggested = NodeRoleDimension.builder().setName("someDimension").build();
    NodeRoleDimension notSuggested = NodeRoleDimension.builder().setName("blah").build();
    NodeRolesData nodeRolesData =
        NodeRolesData.builder()
            .setRoleDimensions(ImmutableSortedSet.of(suggested, notSuggested))
            .build();

    assertThat(
        AutoCompleteUtils.autoComplete(
                network,
                "snapshot",
                Type.NODE_ROLE_DIMENSION_NAME,
                "dim",
                5,
                null,
                nodeRolesData,
                null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of(suggested.getName())));
  }

  @Test
  public void testNodeRoleNameAutocomplete() throws IOException {
    String network = "network";

    NodeRolesData nodeRolesData =
        NodeRolesData.builder()
            .setRoleDimensions(
                ImmutableSortedSet.of(
                    NodeRoleDimension.builder()
                        .setName("someDimension")
                        .setRoles(
                            ImmutableSortedSet.of(
                                new NodeRole("r1", ".*"), new NodeRole("s2", ".*")))
                        .build(),
                    NodeRoleDimension.builder()
                        .setName("someDimension")
                        .setRoles(ImmutableSortedSet.of(new NodeRole("r2", ".*")))
                        .build()))
            .build();

    assertThat(
        AutoCompleteUtils.autoComplete(
                network, "snapshot", Type.NODE_ROLE_NAME, "r", 5, null, nodeRolesData, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of("r1", "r2")));
  }

  @Test
  public void testNodeSpecAutocomplete() throws IOException {
    String network = "network";
    String snapshot = "snapshot";

    CompletionMetadata completionMetadata =
        CompletionMetadata.builder().setNodes(ImmutableSet.of("a", "b")).build();

    NodeRolesData nodeRolesData = NodeRolesData.builder().build();

    assertThat(
        AutoCompleteUtils.autoComplete(
                network, snapshot, Type.NODE_SPEC, "a", 5, completionMetadata, nodeRolesData, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of("a", "&", ",", "\\")));
  }

  @Test
  public void testOspfPropertySpecAutocomplete() throws IOException {
    assertThat(
        AutoCompleteUtils.autoComplete(Type.OSPF_PROPERTY_SPEC, "area", 5).stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of(AREA_BORDER_ROUTER, AREAS)));
  }

  @Test
  public void testPrefixAutocomplete() throws IOException {
    String network = "network";
    String snapshot = "snapshot";

    CompletionMetadata completionMetadata =
        CompletionMetadata.builder()
            .setPrefixes(ImmutableSet.of("1.2.3.4/24", "1.3.2.4/30"))
            .build();

    assertThat(
        AutoCompleteUtils.autoComplete(
                network, snapshot, Type.PREFIX, "1.2", 5, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of("1.2.3.4/24")));
  }

  @Test
  public void testProtocolAutocomplete() throws IOException {
    assertThat(
        AutoCompleteUtils.autoComplete(Type.PROTOCOL, "h", 5).stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of(HTTP.toString(), HTTPS.toString(), SSH.toString())));
  }

  @Test
  public void testReferenceBookAutocomplete() throws IOException {
    ReferenceLibrary library =
        new ReferenceLibrary(
            ImmutableList.of(
                ReferenceBook.builder("b1").build(),
                ReferenceBook.builder("b2").build(),
                ReferenceBook.builder("c1").build()));

    // empty matches all possibilities
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.REFERENCE_BOOK_NAME, "", 5, null, null, library)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of("b1", "b2", "c1")));

    // 'g' matches two books
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.REFERENCE_BOOK_NAME, "B", 5, null, null, library)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of("b1", "b2")));

    // 'g1' matches one book
    assertThat(
        AutoCompleteUtils.autoComplete(
                "network", "snapshot", Type.REFERENCE_BOOK_NAME, "b1", 5, null, null, library)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of("b1")));
  }

  @Test
  public void testRoutingPolicyNameAutocomplete() throws IOException {
    String network = "network";
    String snapshot = "snapshot";

    String suggestion = "somePol";
    String notSuggestion = "blah";

    CompletionMetadata completionMetadata =
        CompletionMetadata.builder()
            .setRoutingPolicyNames(ImmutableSet.of(suggestion, notSuggestion))
            .build();

    assertThat(
        AutoCompleteUtils.autoComplete(
                network,
                snapshot,
                Type.ROUTING_POLICY_NAME,
                "som",
                5,
                completionMetadata,
                null,
                null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of(suggestion)));
  }

  @Test
  public void testRoutingProtocolSpecAutocomplete() throws IOException {
    assertThat(
        AutoCompleteUtils.autoComplete(Type.ROUTING_PROTOCOL_SPEC, "bgp", 5).stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of("bgp", "ibgp", "ebgp")));
  }

  @Test
  public void testStringAutocomplete() {
    Set<String> strings = ImmutableSet.of("abcd", "degf");
    assertThat(getSuggestionsTextSet(stringAutoComplete("d", strings)), equalTo(strings));
    assertThat(
        getSuggestionsTextSet(stringAutoComplete("b", strings)), equalTo(ImmutableSet.of("abcd")));
    // full match and case-insensitive
    assertThat(
        getSuggestionsTextSet(stringAutoComplete("aBCd", strings)),
        equalTo(ImmutableSet.of("abcd")));
  }

  @Test
  public void testStructureNameAutocomplete() throws IOException {
    String network = "network";
    String snapshot = "snapshot";

    String suggested = "someStructure";
    String notSuggested = "blah";

    CompletionMetadata completionMetadata =
        CompletionMetadata.builder()
            .setStructureNames(ImmutableSet.of(suggested, notSuggested))
            .build();

    assertThat(
        AutoCompleteUtils.autoComplete(
                network, snapshot, Type.STRUCTURE_NAME, "str", 5, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of(suggested)));
  }

  @Test
  public void testVrfAutocomplete() throws IOException {
    String network = "network";
    String snapshot = "snapshot";

    String suggested = "someVrf";
    String notSuggested = "blah";

    CompletionMetadata completionMetadata =
        CompletionMetadata.builder().setVrfs(ImmutableSet.of(suggested, notSuggested)).build();

    assertThat(
        AutoCompleteUtils.autoComplete(
                network, snapshot, Type.VRF, "v", 5, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of(suggested)));
  }

  @Test
  public void testZoneAutocomplete() throws IOException {
    String network = "network";
    String snapshot = "snapshot";

    String suggested = "someZone";
    String notSuggested = "blah";

    CompletionMetadata completionMetadata =
        CompletionMetadata.builder().setZones(ImmutableSet.of(suggested, notSuggested)).build();

    assertThat(
        AutoCompleteUtils.autoComplete(
                network, snapshot, Type.ZONE, "z", 5, completionMetadata, null, null)
            .stream()
            .map(AutocompleteSuggestion::getText)
            .collect(Collectors.toSet()),
        equalTo(ImmutableSet.of(suggested)));
  }

  @Test
  public void testAutocompleteUnsupportedType() throws IOException {
    Type type = Type.ANSWER_ELEMENT;

    _thrown.expect(IllegalArgumentException.class);
    _thrown.expectMessage("Unsupported completion type: " + type);

    AutoCompleteUtils.autoComplete("network", "snapshot", type, "blah", 5, null, null, null);
  }
}
