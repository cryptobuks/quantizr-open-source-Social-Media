package quanta.request;

import quanta.request.base.RequestBase;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class GetServerInfoRequest extends RequestBase {

    // command to run before sending back info about the command.
    private String command;

    // we support one up to one parameter to go with the function
    private String parameter;

    // currently selected node on the GUI
    private String nodeId;

}
