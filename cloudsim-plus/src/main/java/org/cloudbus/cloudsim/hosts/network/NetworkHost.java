/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.hosts.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.network.HostPacket;
import org.cloudbus.cloudsim.util.Conversion;
import org.cloudbus.cloudsim.util.Log;
import org.cloudbus.cloudsim.network.switches.EdgeSwitch;
import org.cloudbus.cloudsim.network.VmPacket;
import org.cloudbus.cloudsim.schedulers.cloudlet.network.NetworkCloudletSpaceSharedScheduler;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.schedulers.vm.VmScheduler;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.lists.PeList;
import org.cloudbus.cloudsim.lists.VmList;
import org.cloudbus.cloudsim.provisioners.ResourceProvisioner;

/**
 * NetworkHost class extends {@link HostSimple} to support simulation of
 * networked datacenters. It executes actions related to management of packets
 * (sent and received) other than that of virtual machines (e.g., creation and
 * destruction). A host has a defined policy for provisioning memory and bw, as
 * well as an allocation policy for PE's to virtual machines.
 *
 * <p>Please refer to following publication for more details:
 * <ul>
 * <li>
 * <a href="http://dx.doi.org/10.1109/UCC.2011.24">
 * Saurabh Kumar Garg and Rajkumar Buyya, NetworkCloudSim: Modelling Parallel
 * Applications in Cloud Simulations, Proceedings of the 4th IEEE/ACM
 * International Conference on Utility and Cloud Computing (UCC 2011, IEEE CS
 * Press, USA), Melbourne, Australia, December 5-7, 2011.
 * </a>
 * </ul>
 * </p>
 *
 * @author Saurabh Kumar Garg
 * @since CloudSim Toolkit 3.0
 */
public class NetworkHost extends HostSimple {

    private int totalDataTransferBytes = 0;

    /**
     * A buffer of packets to send for VMs inside this Host.
     */
    private final List<HostPacket> packetsToSendForLocalVms;

    /**
     * A buffer of packets to send for VMs outside this Host.
     */
    private final List<HostPacket> packetsToSendForExternalVms;

    /**
     * List of received packets.
     */
    private final List<HostPacket> hostPacketsReceived;

    /**
     * Edge switch in which the Host is connected.
     */
    private EdgeSwitch edgeSwitch;

    /**
     * @TODO What exactly is this bandwidth? Because it is redundant with the bw
     * capacity defined in {@link Host#getBwProvisioner()}
     *
     * @see #getBandwidth()
     */
    private double bandwidth;

    /**
     * Creates a NetworkHost.
     *
     * @param id the id
     * @param storage the storage capacity
     * @param peList the host's PEs list
     *
     */
    public NetworkHost(int id, long storage, List<Pe> peList) {
        super(id, storage, peList);
        hostPacketsReceived = new ArrayList<>();
        packetsToSendForExternalVms = new ArrayList<>();
        packetsToSendForLocalVms = new ArrayList<>();
    }

    /**
     * Creates a NetworkHost with the given parameters.
     *
     * @param id the id
     * @param ramProvisioner the ram provisioner
     * @param bwProvisioner the bw provisioner
     * @param storage the storage capacity
     * @param peList the host's PEs list
     * @param vmScheduler the VM scheduler
     *
     * @deprecated Use the other available constructors with less parameters
     * and set the remaining ones using the respective setters.
     * This constructor will be removed in future versions.
     */
    @Deprecated
    public NetworkHost(
            int id,
            ResourceProvisioner ramProvisioner,
            ResourceProvisioner bwProvisioner,
            long storage,
            List<Pe> peList,
            VmScheduler vmScheduler)
    {
        this(id, storage, peList);
        setRamProvisioner(ramProvisioner);
        setBwProvisioner(bwProvisioner);
        setVmScheduler(vmScheduler);
    }

    @Override
    public double updateVmsProcessing(double currentTime) {
        double completionTimeOfNextFinishingCloudlet = super.updateVmsProcessing(currentTime);
        receivePackets();
        sendAllPacketListsOfAllVms();

        return  completionTimeOfNextFinishingCloudlet;
    }

    /**
     * Receives packets and forwards them to targeting VMs and respective Cloudlets.
     */
    private void receivePackets() {
        try{
            for (HostPacket netPkt : hostPacketsReceived) {
                netPkt.getVmPacket().setReceiveTime(getSimulation().clock());

                Vm vm = VmList.getById(getVmList(), netPkt.getVmPacket().getDestination().getId());
                NetworkCloudletSpaceSharedScheduler sched = getCloudletScheduler(vm);

                sched.addPacketToListOfPacketsSentFromVm(netPkt.getVmPacket());
                Log.println(
                    Log.Level.DEBUG, getClass(), getSimulation().clock(),
                    "Host %d received pkt with %.0f bytes from Cloudlet %d in VM %d and fowarded it to Cloudlet %d in VM %d",
                    getId(), netPkt.getVmPacket().getSize(),
                    netPkt.getVmPacket().getSenderCloudlet().getId(),
                    netPkt.getVmPacket().getSource(),
                    netPkt.getVmPacket().getReceiverCloudlet().getId(),
                    netPkt.getVmPacket().getDestination());
            }

            hostPacketsReceived.clear();
        } catch(Exception e){
            throw new RuntimeException(
                    "Error when cloudlet was receiving packets at time " + getSimulation().clock(), e);
        }
    }

    /**
     * Gets all packet lists of all VMs placed into the host and send them all.
     * It checks whether a packet belongs to a local VM or to a VM hosted on other machine.
     */
    private void sendAllPacketListsOfAllVms() {
        getVmList().forEach(this::collectAllListsOfPacketsToSendFromVm);
        sendPacketsToLocalVms();
        sendPacketsToExternalVms();
    }

    /**
     * Gets the packets from the local packets buffer and sends them
     * to VMs inside this host.
     */
    private void sendPacketsToLocalVms() {
        for (HostPacket hostPkt : packetsToSendForLocalVms) {
            hostPkt.setSendTime(hostPkt.getReceiveTime());
            hostPkt.getVmPacket().setReceiveTime(getSimulation().clock());
            // insert the packet in receivedlist
            Vm destinationVm = hostPkt.getVmPacket().getDestination();
            getCloudletScheduler(destinationVm).addPacketToListOfPacketsSentFromVm(hostPkt.getVmPacket());
        }

        if (!packetsToSendForLocalVms.isEmpty()) {
            for (Vm vm : getVmList()) {
                vm.updateVmProcessing(
                    getSimulation().clock(), getVmScheduler().getAllocatedMipsForVm(vm));
            }
        }

        packetsToSendForLocalVms.clear();
    }

    /**
     * Gets the packets from the local packets buffer and sends them
     * to VMs outside this host.
     */
    private void sendPacketsToExternalVms() {
        final double availableBwByPacket = getBandwidthByPacket(packetsToSendForExternalVms.size());
        for (HostPacket hostPkt : packetsToSendForExternalVms) {
            double delay = Conversion.bytesToMegaBites(hostPkt.getVmPacket().getSize()) / availableBwByPacket;
            totalDataTransferBytes += hostPkt.getVmPacket().getSize();

            // send to Datacenter with delay
            getSimulation().send(
                    getDatacenter().getId(), getEdgeSwitch().getId(),
                    delay, CloudSimTags.NETWORK_EVENT_UP, hostPkt);
        }

        packetsToSendForExternalVms.clear();
    }

    /**
     * Gets the bandwidth (in  Megabits/s) that will be available for each packet considering a given number of packets
     * that are expected to be sent.
     *
     * @param numberOfPackets the expected number of packets to sent
     * @return the available bandwidth (in  Megabits/s) for each packet or the total bandwidth if the number of packets is 0 or 1
     */
    private double getBandwidthByPacket(double numberOfPackets) {
        return numberOfPackets == 0 ? bandwidth : bandwidth / numberOfPackets;
    }

    private NetworkCloudletSpaceSharedScheduler getCloudletScheduler(Vm vm) {
        return (NetworkCloudletSpaceSharedScheduler) vm.getCloudletScheduler();
    }

    /**
     * Collects all lists of packets of a given Vm
     * in order to get them together to be sent.
     *
     * @param sourceVm the VM from where the packets will be sent
     * @todo @author manoelcampos The class forces the use of a NetworkCloudletSpaceSharedScheduler, what
     * doesn't make sense and will cause runtime class cast exception if a different one is used.
     */
    protected void collectAllListsOfPacketsToSendFromVm(Vm sourceVm) {
        NetworkCloudletSpaceSharedScheduler sched = getCloudletScheduler(sourceVm);
        for (Entry<Vm, List<VmPacket>> es : sched.getHostPacketsToSendMap().entrySet()) {
            collectListOfPacketToSendFromVm(es);
        }
    }

    /**
     * Collects all packets of a specific packet list of a given Vm
     * in order to get them together to be sent.
     *
     * @param es The Map entry from a packet map where the key is the
     * sender VM id and the value is a list of packets to send
     */
    protected void collectListOfPacketToSendFromVm(Entry<Vm, List<VmPacket>> es) {
        List<VmPacket> vmPktList = es.getValue();
        for (VmPacket vmPkt : vmPktList) {
            HostPacket hostPkt = new HostPacket(this, vmPkt);
            //Checks if the VM is inside this Host
            Vm receiverVm = VmList.getById(this.getVmList(), vmPkt.getDestination().getId());
            if (receiverVm != Vm.NULL) {
                packetsToSendForLocalVms.add(hostPkt);
            } else {
                packetsToSendForExternalVms.add(hostPkt);
            }
        }
    }

    /**
     * Gets the maximum utilization among the PEs of a given VM.
     *
     * @param vm The VM to get its PEs maximum utilization
     * @return The maximum utilization among the PEs of the VM.
     */
    public double getMaxUtilizationAmongVmsPes(Vm vm) {
        return PeList.getMaxUtilizationAmongVmsPes(getPeList(), vm);
    }

    public EdgeSwitch getEdgeSwitch() {
        return edgeSwitch;
    }

    public void setEdgeSwitch(EdgeSwitch sw) {
        this.edgeSwitch = sw;
        this.bandwidth = sw.getDownlinkBandwidth();
    }

    public int getTotalDataTransferBytes() {
        return totalDataTransferBytes;
    }

    /**
     * Adds a packet to the list of received packets in order
     * to further submit them to the respective target VMs and Cloudlets.
     *
     * @param hostPacket received network packet
     */
    public void addReceivedNetworkPacket(HostPacket hostPacket){
        hostPacketsReceived.add(hostPacket);
    }

    /**
     * Gets the Host bandwidth capacity in  Megabits/s.
     * @return
     */
    public double getBandwidth() {
        return bandwidth;
    }

    /**
     * Sets the Host bandwidth capacity in  Megabits/s.
     * @param bandwidth the bandwidth to set
     */
    public void setBandwidth(double bandwidth) {
        this.bandwidth = bandwidth;
    }
}
