package org.cloudbus.cloudsim.examples.network.datacenter;

import java.util.Arrays;
import java.util.List;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.network.datacenter.AppCloudlet;
import org.cloudbus.cloudsim.network.datacenter.NetworkCloudlet;
import org.cloudbus.cloudsim.network.datacenter.NetworkVm;
import org.cloudbus.cloudsim.network.datacenter.Task;
import org.cloudbus.cloudsim.network.datacenter.Task.Stage;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModel;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;

/**
 * An example of a Workflow {@link AppCloudlet}'s that are composed of 
 * 3 {@link NetworkCloudlet}, each one having different stages
 * such as sending, receiving or processing data.
 * 
 * @author Saurabh Kumar Garg
 * @author Rajkumar Buyya
 * @author Manoel Campos da Silva Filho
 * 
 * @todo @author manoelcampos The example isn't working yet.
 * It freezes after the cloudlets creation.
 * Maybe the problem is in the NetworkCloudletSpaceSharedScheduler class.
 */
public class NetworkVmsExampleWorkflowAppCloudlet extends NetworkVmsExampleAppCloudletAbstract {
    public NetworkVmsExampleWorkflowAppCloudlet(){
        super();
    }

    /**
     * Starts the execution of the example.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        new NetworkVmsExampleWorkflowAppCloudlet();
    }
    
    /**
     * Create a list of NetworkCloudlets that together represent
     * the sub-applications of a Workflow AppCloudlet.
     *
     * @param appCloudlet
     * @return the list of created NetworkCloudlets
     */
    @Override
    public List<NetworkCloudlet> createNetworkCloudlets(
            AppCloudlet appCloudlet) {
        NetworkCloudlet networkCloudletList[] = new NetworkCloudlet[3];
        List<NetworkVm> selectedVms = randomlySelectVmsForAppCloudlet(getBroker(), networkCloudletList.length);
        
        for(int i = 0; i < networkCloudletList.length; i++){         
            networkCloudletList[i] = createNetworkCloudlet(i, appCloudlet, selectedVms.get(i));
            Log.printFormattedLine(
                "Created NetworkCloudlet %d for AppCloudlet %d", 
                networkCloudletList[i].getId(), appCloudlet.getId());
        }

        //Task A (id 0)
        addExecutionTask(networkCloudletList[0], selectedVms.get(0));
        addSendOrReceiveTask(networkCloudletList[0], Stage.WAIT_SEND, networkCloudletList[2]);

        //Task B (id 1)
        addExecutionTask(networkCloudletList[1], selectedVms.get(1));
        addSendOrReceiveTask(networkCloudletList[1], Stage.WAIT_SEND,  networkCloudletList[2]);
        
        //Task C (id 2)
        addSendOrReceiveTask(networkCloudletList[2], Stage.WAIT_RECV, networkCloudletList[0]);
        addSendOrReceiveTask(networkCloudletList[2], Stage.WAIT_RECV, networkCloudletList[1]);
        addExecutionTask(networkCloudletList[2], selectedVms.get(0));

        return Arrays.asList(networkCloudletList);
    }

    /**
     * Adds an send or receive task to list of tasks of the given {@link NetworkCloudlet}.
     * 
     * @param sourceNetCloudlet the {@link NetworkCloudlet} to add the task
     * @param stage The stage to set to the created task
     * @param destinationNetCloudlet the destination where to send or from which is 
     * expected to receive data
     */
    private void addSendOrReceiveTask(
            NetworkCloudlet sourceNetCloudlet, Task.Stage stage,
            NetworkCloudlet destinationNetCloudlet) {        
        Task task = new Task(
                sourceNetCloudlet.getTasks().size(), stage, 100, 0, NETCLOUDLET_RAM,
                sourceNetCloudlet.getVmId(), destinationNetCloudlet.getId());
        sourceNetCloudlet.getTasks().add(task);
    }

    /**
     * Adds an execution task to list of tasks of the given {@link NetworkCloudlet}.
     * 
     * @param netCloudlet the {@link NetworkCloudlet} to add the task
     * @param vm the VM where to send or from which to receive data
     */
    private static void addExecutionTask(NetworkCloudlet netCloudlet, Vm vm) {
        /**
         * @todo @author manoelcampos It's strange
         * to define the time of the execution task.
         * It would be defined the length instead.
         * In this case, the execution time will
         * depend on the MIPS of the 
         * PE where the task is being executed.
         */
        Task stage = new Task(
                netCloudlet.getTasks().size(), 
                Task.Stage.EXECUTION, 0, netCloudlet.getCloudletLength(), NETCLOUDLET_RAM,
                vm.getId(), netCloudlet.getId());
        netCloudlet.getTasks().add(stage);
    }

    /**
     * Creates a {@link NetworkCloudlet} for the given {@link AppCloudlet}.
     * 
     * @param networkCloudletId the id of the {@link NetworkCloudlet} to be created
     * @param appCloudlet the {@link AppCloudlet} that will own the created {@link NetworkCloudlet)
     * @param vm the VM that will run the created {@link NetworkCloudlet)
     * @return 
     */
    private NetworkCloudlet createNetworkCloudlet(int networkCloudletId, AppCloudlet appCloudlet, Vm vm) {
        UtilizationModel utilizationModel = new UtilizationModelFull();
        NetworkCloudlet netCloudlet = new NetworkCloudlet(
                networkCloudletId, 0, 1, 
                NETCLOUDLET_FILE_SIZE, NETCLOUDLET_OUTPUT_SIZE, NETCLOUDLET_RAM,
                utilizationModel, utilizationModel, utilizationModel);
        netCloudlet.setAppCloudlet(appCloudlet);
        netCloudlet.setUserId(getBroker().getId());
        netCloudlet.submittime = CloudSim.clock();
        netCloudlet.setVmId(vm.getId());
        return netCloudlet;
    }

}