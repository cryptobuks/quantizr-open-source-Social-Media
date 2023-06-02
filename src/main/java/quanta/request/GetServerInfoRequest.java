
package quanta.request;

import quanta.request.base.RequestBase;

public class GetServerInfoRequest extends RequestBase {
    // command to run before sending back info about the command.
    private String command;
    // we support one up to one parameter to go with the function
    private String parameter;
    // currently selected node on the GUI
    private String nodeId;

    
    public String getCommand() {
        return this.command;
    }

    
    public String getParameter() {
        return this.parameter;
    }

    
    public String getNodeId() {
        return this.nodeId;
    }

    
    public void setCommand(final String command) {
        this.command = command;
    }

    
    public void setParameter(final String parameter) {
        this.parameter = parameter;
    }

    
    public void setNodeId(final String nodeId) {
        this.nodeId = nodeId;
    }

    public GetServerInfoRequest() {
    }
}
